package rearview.util

import java.io.File
import java.io.FilePermission
import java.io.Writer
import java.lang.reflect.ReflectPermission
import java.net.NetPermission
import java.net.URL
import java.security.AccessControlContext
import java.security.AccessController
import java.security.CodeSource
import java.security.Permissions
import java.security.PrivilegedAction
import java.security.ProtectionDomain
import java.security.cert.Certificate
import java.util.concurrent.atomic.AtomicInteger
import org.jruby.CompatVersion
import org.jruby.RubyInstanceConfig.CompileMode
import org.jruby.ast.ClassNode
import org.jruby.ast.ClassVarAsgnNode
import org.jruby.ast.CallNode
import org.jruby.ast.FCallNode
import org.jruby.ast.GlobalAsgnNode
import org.jruby.ast.InstAsgnNode
import org.jruby.ast.Node
import org.jruby.embed.LocalContextScope
import org.jruby.embed.LocalVariableBehavior
import org.jruby.embed.PathType
import org.jruby.embed.ScriptingContainer
import scala.collection.JavaConversions._
import scala.concurrent.stm.atomic
import play.api.Play.current
import rearview.Global
import rearview.Global.sandboxTimeout
import play.api.Logger

object JRubyUtils {

  private lazy val jrubyLoadPaths = current.configuration.getString("jruby.loadpaths").getOrElse(sys.error("jruby.loadpaths must be defined in configuration"))
  private lazy val jrubyScript    = current.configuration.getString("jruby.script").getOrElse(sys.error("jruby.script must be defined in configuration"))

  lazy val jrubyContainer = scriptContainer

  /**
   * Returns a script container initialized consistently
   */
  def scriptContainer = {
    val container = new ScriptingContainer(LocalContextScope.CONCURRENT, LocalVariableBehavior.TRANSIENT)
    container.setCompatVersion(CompatVersion.RUBY1_9)
    container.setCompileMode(CompileMode.OFF)
    container.setLoadPaths(jrubyLoadPaths :: Nil)
    container
  }

  /**
   * Evaluate the ruby expression with the given namespace inside the sandbox
   */
  def instantiateWrapper(container: ScriptingContainer, writer: Writer, namespace: Map[String, Any]) = {
    container.put("timeout", sandboxTimeout)
    container.put("writer", writer)
    container.put("namespace", mapAsJavaMap(namespace))

    container.runScriptlet(PathType.CLASSPATH, jrubyScript)
  }

  /**
   * Reduces permissions for user supplied code to eval
   */
  def sandboxEval(container: ScriptingContainer, receiver: Object, expression: String) = {
    val codeSource = new CodeSource(new URL("file:/-"), Array[Certificate]())

    val permissions = new Permissions()
    permissions.add(new RuntimePermission("createClassLoader")) // this is a dangerous permission
    permissions.add(new RuntimePermission("getClassLoader"))
    permissions.add(new ReflectPermission("suppressAccessChecks"))
    permissions.add(new NetPermission("specifyStreamHandler"))
    Option(new File("lib/rubygems.jar")).foreach { f =>
      permissions.add(new FilePermission(f.getAbsolutePath(), "read")) // dev only
    }
    permissions.setReadOnly()

    val domains = Array[ProtectionDomain](new ProtectionDomain(codeSource, permissions))
    val accessControlContext = new AccessControlContext(domains)

    val action = new PrivilegedAction[Object]() {
      override def run(): Object = {
        container.callMethod(receiver, "secure_eval", Array[Object](expression), classOf[Object])
      }
    }

    // Counter-intuitively using doPrivileged to *lower* permissions:
    AccessController.doPrivileged(action, accessControlContext)
  }

  /**
   * Perform a depth-first search for any nodes in the AST
   * we deem insecure such as class definitions, import, eval, etc.
   */
  def inspectAST(tree: Node): Option[String] = {
    def depthFirst(nodes: List[Node]): Option[String] = {
      nodes.headOption flatMap { node =>
        isProhibitedNode(node)
      } orElse {
        nodes match {
          case node :: rest => depthFirst(node.childNodes().toList) orElse {
            depthFirst(rest)
          }

          case Nil => None
        }
      }
    }

    isProhibitedNode(tree).orElse(depthFirst(tree.childNodes().toList))
  }


  /**
   * Matches on one of the Nodes deemed prohibited
   */
  def isProhibitedNode(node: Node): Option[String] = node match {
    case cls: ClassNode                            => Some("Class creation or extensions forbidden")
    case cls: ClassVarAsgnNode                     => Some("Assigning class vars is prohibited")
    case cls: InstAsgnNode                         => Some("Assigning instance vars is prohibited")
    case fn:  CallNode  if(isEval(fn.getName))     => Some("eval is forbidden")
    case fn:  FCallNode if(isEval(fn.getName))     => Some("eval is forbidden")
    case fn:  CallNode  if(isInclude(fn.getName))  => Some("include/require are forbidden")
    case fn:  FCallNode if(isInclude(fn.getName))  => Some("include/require are forbidden")
    case fn:  CallNode  if(isSend(fn.getName))     => Some("send is prohibited")
    case fn:  FCallNode if(isSend(fn.getName))     => Some("send is prohibited")
    case global: GlobalAsgnNode                    => Some("Globals are forbidden")
    case _                                         => None
  }

  /**
   * Return true if the function call is some sort of eval.
   */
  def isEval(fnCall: String): Boolean = fnCall match {
    case "eval"          => true
    case "class_eval"    => true
    case "instance_eval" => true
    case "module_eval"   => true
    case _               => false
  }

  /**
   * Return true if include or require are called
   */
  def isInclude(fnCall: String): Boolean = fnCall match {
    case "include" => true
    case "require" => true
    case _         => false
  }

  /**
   * Return true if some sort of send
   */
  def isSend(fnCall: String): Boolean = fnCall match {
    case "send"        => true
    case "__send__"    => true
    case "public_send" => true
    case _             => false
  }

  class WrappedContainer(container: ScriptingContainer) {
    def eval[T](receiver: Object, expression: String) = {
      val evalUnit = container.parse(expression)
      inspectAST(evalUnit.getNode()) map { msg =>
        throw new SecurityException(s"Security Violation: $msg")
      } orElse {
        Some(sandboxEval(container, receiver, expression).asInstanceOf[T])
      } get
    }
  }

  implicit def containerToWrapped(container: ScriptingContainer) = new WrappedContainer(container)

  import scala.concurrent.stm._

  object JRubyContainerCache {
    val counter   = new AtomicInteger(1)
    var container = Ref(scriptContainer)

    def get: ScriptingContainer = {
      atomic { implicit txn =>
        if((counter.getAndIncrement() % Global.jrubyCacheIterations) == 0) {
          Logger.info("*** Flushing JRuby container")
          container().terminate()
          container.swap(scriptContainer)
        }

        container()
      }
    }
  }
}

