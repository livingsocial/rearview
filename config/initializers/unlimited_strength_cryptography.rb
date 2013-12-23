# https://github.com/jruby/jruby/wiki/UnlimitedStrengthCrypto
begin
  java.lang.Class.for_name('javax.crypto.JceSecurity').get_declared_field('isRestricted').tap{|f| f.accessible = true; f.set nil, false}
rescue
end
