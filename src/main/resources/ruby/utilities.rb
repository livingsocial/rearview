#
# Add re-usable code/functions in this module
#
class Array
  def mean
    self.sum / self.length
  end


  def median
    sorted = self.sort
    mid    = self.length / 2
    if self.length.odd?
      sorted[mid].to_f
    else
      (sorted[mid-1] + sorted[mid]).to_f / 2.0
    end
  end


  def sum
    self.inject(0) { |total, n| total + n.to_f }
  end


  def percentile(number)
    position = (number > 1) ? (number.to_f / 100) : number
    arr = self.map { |x| x || 0 }
    arr.sort[(arr.length * position) - 1]
  end
end


module MonitorUtilities
  # def anovaF(*metrics)
  #   data = metrics.map { |m| m.to_java(:double) }
  #   TestUtils.oneWayAnovaFValue(data)
  # end


  # def anovaP(*metrics)
  #   data = metrics.map { |m| m.to_java(:double) }
  #   TestUtils.oneWayAnovaPValue(data)
  # end


  # def anovaTest(alpha, *metrics)
  #   data = metrics.map { |m| m.to_java(:double) }
  #   TestUtils.oneWayAnovaTest(data, alpha)
  # end


  def deploy_check(num_points, deploy, metric)
    if metric == deploy
      raise "You've passed the deploy metric to be analyzed against itself, which is not a valid analysis."
    elsif metric.values.size < (num_points * 2) + 1
      raise "Not enough data to evaluate. There must be #{num_points} data points before and after a deploy."
    else
      results = []
      last_deploy = deploy.values.rindex { |v| !v.nil? }

      if last_deploy
        deploy_time = deploy.entries[last_deploy].timestamp

        # If the num_points after the deploy is true then
        if metric.entries.drop_while { |entry| entry.timestamp <= deploy_time }.length == num_points
          before = metric.values.last((num_points * 2) + 1).first(num_points).sum
          after  = metric.values.last(num_points).sum
          delta  = before == 0 ? 0.0 : ((after - before) / before) * 100

          results = [metric.label, before, after, delta]
        end
      end

      results
    end
  end
end # Class end
