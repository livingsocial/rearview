#!/bin/bash

ROOT_DIR=`dirname $0`

. $HOME/.rvm/scripts/rvm

cd $ROOT_DIR

rvm install jruby
. .rvmrc

rm -rf vendor

bundle install --no-deployment
bundle install --deployment

jar cf ../lib/rubygems.jar -C vendor/bundle/jruby/1.9 .

rm -rf vendor
