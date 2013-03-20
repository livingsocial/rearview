#
# Do not edit unless you know what you're doing!!! This code affects the sandbox behavior
# and monitor functionality.
#

require 'json'
require 'timeout'
require 'utilities'

class Metric
  attr_reader :timestamp, :value

  def initialize(label_fn, timestamp, value)
    @label_fn = label_fn
    @timestamp = timestamp
    @value     = value
  end

  def label
     @label_fn.call
   end

  def to_s
    "{ label: #{label}, timestamp: #{timestamp}, value: #{value.nil? ? "nil" : value.to_f} }"
  end
end


class TimeSeries
  attr_accessor :label
  attr_reader :entries

  def initialize(ts)
    @label   = ts.first["metric"]
    @entries = ts.map { |t| Metric.new(lambda { self.label }, t["timestamp"], t["value"]) }
    def @entries.to_s
      "[ #{self.join ", "} ]"
    end
  end

  def values
    @entries.map { |e| e.value }
  end

  def to_s
    "{ label: #{@label}, entries: [ #{@entries.join ", "} ] }"
  end
end


class Scoped
  include MonitorUtilities

  attr_reader :graph_data

  def initialize(writer)
    @graph_data         = {}
    @graph_data.default = [] # make the default value an empty array
    @writer             = writer
  end

  def puts(s)
    @writer.append(s.to_s + "\n")
  end

  def p(s)
    @writer.append(s.to_s + "\n")
  end

  def graph_value(name, timestamp, value)
    @graph_data[name] += [[timestamp, value]]
  end

  def fold_metrics(initial, &block)
    iterations = @timeseries[0].values.length
    iterations.times.inject(initial) do |accum, i|
      entries = @timeseries.map { |series| series.entries[i] }
      block.call accum, *entries
    end
  end

  def with_metrics(&block)
    iterations = @timeseries[0].values.length
    iterations.times.each do |i|
      entries = @timeseries.map { |series| series.entries[i] }
      block.call *entries
    end
  end

  def generate_default_graph_data
    data = @timeseries.map { |ts|
      [ts.label, ts.entries.map { |e| [e.timestamp, e.value] } ]
    }
    @graph_data.merge!(Hash[data])
  end

  def scoped_eval(expression, namespace, timeout)
    # Copy instance variables from top-level class into current scope
    namespace.keys.each do |v|
      instance_variable_set "#{v}".to_sym, namespace[v]
    end

    # Bolt on a to_s to @timeseries to pretty print the instances
    def @timeseries.to_s
      "[ #{self.join ", "} ]"
    end

    begin
      instance_eval <<-EOF
        Timeout::timeout(#{timeout}) {
          graph_value = lambda { |name, timestamp, value| graph_value(name, timestamp, value) }
          #{expression}
          nil
        }
      EOF
    rescue Timeout::Error => e
      raise java.lang.SecurityException.new(e.to_s)
    rescue RuntimeError => e
      puts e.message
      e.message
    ensure
      generate_default_graph_data if @graph_data.empty?
    end
  end
end


class Wrapper
  def initialize(timeout, writer)
    @timeout = timeout
    @writer  = writer
  end

  def create_timeseries(java_ts)
    ns   = {}
    ts   = java_ts.to_a.map { |t| TimeSeries.new(t) }
    vars = (0...ts.length).map do |i|
      varname = variable_by_offset(i)
      ns["@#{varname}"] = ts[i]
    end
    ns["@timeseries"] = ts
    ns
  end

  #
  # Return a variable name (e.g. a,b,c) from an ordinal
  #
  def variable_by_offset(index)
    offset = 'a'.ord
    dv  = index.divmod(26)
    ext = dv[0] > 0 ? dv[1].to_s : ""
    (offset + dv[1]).chr + ext
  end


  # Binding needs to be relative to the class, NOT top level
  def secure_eval(expression)
    scoped = Scoped.new(@writer)
    @graph_data = scoped.graph_data #lame
    ts = create_timeseries(@namespace.to_hash.delete "@timeseries")
    ns = @namespace.to_hash.merge(ts)
    scoped.scoped_eval(expression, ns, @timeout)
  end
end

Wrapper.new timeout, writer
