# encoding: UTF-8
# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# Note that this schema.rb definition is the authoritative source for your
# database schema. If you need to create the application database on another
# system, you should be using db:schema:load, not running all the migrations
# from scratch. The latter is a flawed and unsustainable approach (the more migrations
# you'll amass, the slower it'll run and the greater likelihood for issues).
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema.define(version: 20140107173034) do

  create_table "applications", force: true do |t|
    t.integer  "user_id"
    t.string   "name",        null: false
    t.datetime "created_at"
    t.datetime "updated_at"
    t.datetime "deleted_at"
    t.string   "ancestry"
    t.string   "description"
  end

  add_index "applications", ["ancestry"], name: "index_applications_on_ancestry", using: :btree
  add_index "applications", ["user_id"], name: "user_id", using: :btree

  create_table "job_data", force: true do |t|
    t.integer  "job_id",                        default: 0, null: false
    t.datetime "created_at"
    t.datetime "updated_at"
    t.text     "data",       limit: 2147483647,             null: false
  end

  add_index "job_data", ["job_id"], name: "index_job_data_on_job_id", using: :btree

  create_table "job_errors", force: true do |t|
    t.integer  "job_id"
    t.datetime "created_at"
    t.text     "message",         limit: 2147483647
    t.string   "status"
    t.datetime "last_alerted_at"
    t.datetime "updated_at"
  end

  add_index "job_errors", ["job_id"], name: "job_id", using: :btree
  add_index "job_errors", ["status"], name: "index_job_errors_on_status", using: :btree

  create_table "jobs", force: true do |t|
    t.datetime "created_at"
    t.datetime "updated_at"
    t.string   "name",                                      null: false
    t.boolean  "active",                     default: true, null: false
    t.datetime "last_run"
    t.string   "cron_expr",     limit: 1024,                null: false
    t.string   "status"
    t.integer  "user_id"
    t.text     "alert_keys"
    t.datetime "deleted_at"
    t.integer  "error_timeout",              default: 60,   null: false
    t.datetime "next_run"
    t.text     "description"
    t.integer  "app_id",                                    null: false
    t.text     "metrics",                                   null: false
    t.text     "monitor_expr"
    t.integer  "minutes"
    t.text     "to_date"
  end

  add_index "jobs", ["app_id"], name: "app_id", using: :btree
  add_index "jobs", ["id", "name"], name: "id_name_version_key", unique: true, using: :btree
  add_index "jobs", ["status"], name: "index_jobs_on_status", using: :btree
  add_index "jobs", ["user_id"], name: "jobs_ibfk_1", using: :btree

  create_table "users", force: true do |t|
    t.datetime "created_at"
    t.datetime "updated_at"
    t.string   "encrypted_password", default: "", null: false
    t.string   "email",                           null: false
    t.string   "first_name"
    t.string   "last_name"
    t.datetime "last_login"
    t.text     "preferences"
  end

  add_index "users", ["email"], name: "email", unique: true, using: :btree

end
