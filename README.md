[![GitHub version](https://badge.fury.io/gh/livingsocial%2Frearview.png)](http://badge.fury.io/gh/livingsocial%2Frearview)

Overview
========

Rearview is a real-time monitoring framework that sits on top of <a href="https://github.com/graphite-project" target="_blank">Graphite</a>'s time series data. This allows users to create monitors that both visualize and alert on data as it streams from Graphite. The monitors themselves are simple Ruby scripts which run in a sandbox to provide additional security. Monitors are also configured with a crontab compatible time specification used by the scheduler. Alerts can be sent via email, pagerduty, or campfire.

![rearview sample monitor](https://github.com/livingsocial/rearview/wiki/sample-monitor.png)

This is a port of the [original](https://github.com/livingsocial/rearview/tree/scala-1.0.0) rearview re-written as a Ruby on Rails application. Although the bulk of the code has been running in production for almost a year, it was refactored to work as a rails engine to better suit open sourcing. Currently we consider this release candidate quality, and welcome contributions.

Go [here](https://github.com/livingsocial/rearview/wiki/Overview) for a more detailed overview of rearview

Requirements
============

  - jvm 1.6+
  - jruby 1.7.5+
  - ruby manager (rvm or rbenv)
  - graphite
  - mysql/postgresql (or other supported [database](https://github.com/jruby/activerecord-jdbc-adapter))

Getting Started
===============

### Get it

[Download](https://github.com/livingsocial/rearview/archive/v1.2.0.zip) the latest release.

### Database Support

Rearview comes with drivers for both mysql and postgresql. If you wish to use a differenct database select a supported
jdbc driver and add it to the Gemfile and bundle install. See [activerecord-jdbc-adapter](https://github.com/jruby/activerecord-jdbc-adapter) site for more details.

### Edit config/database.yml

Configure per your selected database driver and database connection settings. See [Configuring Rails](http://guides.rubyonrails.org/configuring.html#configuring-active-record) for more details. The installation defaults to common settings for mysql.

Sample configurations for both mysql (config/database.jdbcmysql.yml) and postgresql (config/database.jdbcpostgresql.yml) are available.

### Run the setup script

    $ bin/setup

If the setup script fails due to **java.lang.ClassNotFoundException: javax/crypto/JceSecurity**, please see issue [#17](https://github.com/livingsocial/rearview/issues/17) for a resolution to this problem.

Configuration
=============

Before running rearview you must specify a few settings. The configuration file location is:

    config/initializers/rearview.rb

You must set **config.graphite_url** and **config.sandbox_exec** for rearview to run properly. Most of the other settings you should be able to leave as is.

Verify configuration

    $ rake RAILS_ENV=production rearview:config:verify

Running
=======

    $ foreman start

This will start rearview on port 3000 (http://localhost:3000).

Sign-in with the default user **admin@localhost** and password **admin**

Contributing
============

We encourage you to contribute to Rearview. Please check out the [rearview-engine](http://github.com/livingsocial/rearview-engine) repository for more details.


## Team

<table>
  <thead>
    <tr>
      <td>Name</td><td>Role</td><td>Twitter</td><td>GitHub</td>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Steve Akers</td><td>Product Manager</td><td><a href="https://twitter.com/SteveAkers">@SteveAkers</a></td><td><a href="https://github.com/steveakers">https://github.com/steveakers</a></td>
    </tr>
    <tr>
      <td>Trent Albright</td><td>Architect/Lead developer</td><td><a href="https://twitter.com/trent_albright">@trent_albright</a></td><td><a href="https://github.com/talbright">https://github.com/talbright</a></td>
    </tr>
    <tr>
      <td>Ian Quattlebaum</td><td>Lead Front End developer</td><td><a href="https://twitter.com/ianqueue">@ianqueue</a></td><td><a href="https://github.com/ianqueue">https://github.com/ianqueue</a></td>
    </tr>
    <tr>
      <td>Jeff Simpson</td><td>Architect/Lead developer</td><td><a href="https://twitter.com/fooblahblah">@fooblahblah</a></td><td><a href="https://github.com/fooblahblah">https://github.com/fooblahblah</a></td>
    </tr>
</tbody>
</table>
