require 'rubygems'
require 'bundler'
require 'pp'

Bundler.setup

require 'json'

def header(name)
  puts "=" * 80
  puts name
  puts "=" * 80
end

header("RUBY")
puts "#{RUBY_DESCRIPTION}"

header("ENV")
pp ENV

header("LOAD_PATH")
pp $LOAD_PATH

