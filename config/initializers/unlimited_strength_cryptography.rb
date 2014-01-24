# https://github.com/jruby/jruby/wiki/UnlimitedStrengthCrypto
begin
  java.lang.Class.for_name('javax.crypto.JceSecurity').get_declared_field('isRestricted').tap{|f| f.accessible = true; f.set nil, false}
rescue
  Rails.logger.warn <<-EOF

    You may not have unlimited strength cryptography installed. There's a
    known issue with Rails 4.0, jruby, and cryptography in the JVM. Please visit
    https://github.com/jruby/jruby/wiki/UnlimitedStrengthCrypto if you encounter
    failures during startup. If things are running fine you can ignore this message
    and remove the intializer.

    Exception: #{$!}
    Trace: #{$@.join("\n")}
  EOF
end
