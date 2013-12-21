require 'rearview'

Rearview.configure do |config|

  config.logger = Rails.logger
  config.sandbox_dir = Rails.root + "sandbox"
  config.sandbox_exec = ["rvm-exec","ruby-1.9.3-p448@rearview-sandbox","ruby"]
  config.sandbox_timeout = 10

  case Rails.env
    when "test"
      config.preload_jobs = false
    when "production"
      config.sandbox_exec = ["/bin/env","-i","PATH=/opt/ruby-1.9.3/bin", "bundle", "exec", "ruby"]
      config.verify = true
    when "development"
      config.enable_alerts = false
    else
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

