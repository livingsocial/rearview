# This template monitors a metric and if it detects nil (no data) for over a minute it alerts
raise "Custom raise message goes here" if (@a.values.inject :+) == 0