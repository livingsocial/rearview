require 'rearview'

Rearview.configure do |config|

  config.logger = Rails.logger
  config.sandbox_dir = Rails.root + "sandbox"

  # Configure the path to a ruby 1.9.3 binary that will be used to excute your
  # monitor script in the sandbox.
  # ex:
  # config.sandbox_exec=["/opt/ruby-1.9.3/bin/ruby"]
  config.sandbox_exec = ["rvm-exec","ruby-1.9.3@rearview-sandbox","ruby"]

  # How long to wait for a monitor script to run in seconds. After this time
  # period the monitor script will be terminated.
  config.sandbox_timeout = 10

  # This must poing to the graphite web server
  # ex:
  # config.graphite_url="http://graphite.mycompany.com"
  config.graphite_url = nil

  # This is the email from: address used when sending alerts
  config.default_from = "rearview@localhost"

  case Rails.env
    when "test"
      config.preload_jobs = false
    when "production"
      # Monitor script excutable will likely be different in production.
      config.sandbox_exec = ["/bin/env","-i","PATH=/opt/ruby-1.9.3/bin", "bundle", "exec", "ruby"]
      config.verify = true
  end

  if File.basename($0) == "rake"
    config.enable_monitor = false
  end

  # Options passed via environment will override anything else set to this point...
  if ENV['REARVIEW_OPTS'].present?
    config.with_argv(ENV['REARVIEW_OPTS'].try(:split))
  end

end

Rearview.boot!

