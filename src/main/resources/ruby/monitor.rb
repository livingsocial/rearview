#
# Do not edit unless you know what you're doing!!! This code affects the sandbox behavior
# and monitor functionality.
#

require 'json'
require 'stringio'
# insert utilities here
%s

class Metric
  attr_reader :timestamp, :value

  def initialize(label_fn, timestamp, value)
    @label_fn  = label_fn
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

  def initialize(timeout)
    @graph_data         = {}
    @graph_data.default = [] # make the default value an empty array
    @timeout            = timeout
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

  def scoped_eval(namespace)
    # Copy instance variables from top-level class into current scope
    namespace.keys.each do |v|
      instance_variable_set "#{v}".to_sym, namespace[v]
    end

    # Bolt on a to_s to @timeseries to pretty print the instances
    def @timeseries.to_s
      "[ #{self.join ", "} ]"
    end

    out = StringIO.new

    t = Thread.start do
      $stdout = out
      $SAFE=3
      begin
        graph_value = lambda { |name, timestamp, value| graph_value(name, timestamp, value) }
        # This is the eval expression for the monitor
        %s
      rescue RuntimeError => e
        out.write e
        @error = e.to_s
      end
    end

    t.abort_on_exception = false

    begin
      t.join(@timeout)
    rescue => e
      out.write e
      @error = e.to_s
    ensure
      $SAFE = 0
      $stdout = STDOUT
      $stderr = STDERR
    end

    { :graph_data => @graph_data, :output => out.string, :error => @error}.to_json
  end
end


class Wrapper
  def create_timeseries(ts)
    ns   = {}
    ts   = ts.to_a.map { |t| TimeSeries.new(t) }
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
  def secure_eval(namespace)
    scoped = Scoped.new(%s)
    ts = create_timeseries(namespace.to_hash.delete "@timeseries")
    ns = namespace.to_hash.merge(ts)
    puts scoped.scoped_eval(ns)
  end
end

json = <<-'EOF'
%s
EOF

Wrapper.new.secure_eval(JSON.parse(json))
