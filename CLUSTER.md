Running Rearview in cluster mode
================================

To run Rearview as a singleton cluster (the default), simply start it as normal:

    ./sbt start

Local Cluster
-------------

To enable cluster mode a few params have to be specified, netty.port, netty.hostname and seedNodes.  The following is an example creating a local, stand-alone cluster:

    ./sbt -Dclustered=true -Dakka.remote.netty.port=2551 -Dakka.remote.netty.hostname=127.0.0.1 -DseedNodes="akka://JobClusterSystem@127.0.0.1:2551" start

The server will start and be elected as the leader.  Subsequent clients will connect to the leader/seednode upon start.  Note:  The seednode does not always imply the leader, but usually it tends to work out that way.


Remote Cluster
--------------

The next example runs the cluster on a different subnet used by VirtualBox.

For the server:

    ./sbt -Dclustered=true -Dakka.remote.netty.port=2551 -Dakka.remote.netty.hostname=192.168.1.1 -DseedNodes="akka://JobClusterSystem@192.168.1.1:2551" start

For the client (the server is 192.168.1.1):

    ./sbt -Dclustered=true -Dakka.remote.netty.hostname=192.168.1.2 -DseedNodes="akka://JobClusterSystem@192.168.1.1:2551" -Ddb.default.url="jdbc:mysql://192.168.1.1:3306/rearview" start
