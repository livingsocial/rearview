# Add your own tasks in files placed in lib/tasks ending in .rake,
# for example lib/tasks/capistrano.rake, and they will automatically be available to Rake.

# prevent rearview from starting up jobs or firing alerts when rake commands are invoked
unless ENV['REARVIEW_OPTS']
    ENV['REARVIEW_OPTS'] = "--no-preload --no-alerts"
end

require File.expand_path('../config/application', __FILE__)

RearviewWeb::Application.load_tasks
