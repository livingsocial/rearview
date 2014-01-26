# This migration comes from rearview (originally 20131106162900)
class BaseSchema < ActiveRecord::Migration
  def up
    create_table "applications", :force => true do |t|
      t.integer   "user_id"
      t.string    "name",        :null => false
      t.datetime "created_at"
      t.datetime "updated_at"
      t.datetime "deleted_at"
      t.string    "ancestry"
      t.string    "description"
    end

    add_index "applications", ["ancestry"], :name => "index_applications_on_ancestry"
    add_index "applications", ["user_id"], :name => "user_id"

    create_table "job_data", :force => true do |t|
      t.integer   "job_id",                           :default => 0, :null => false
      t.datetime "created_at"
      t.datetime "updated_at"
      t.text      "data",       :limit => 1073741823,                :null => false
    end

    create_table "job_errors", :force => true do |t|
      t.integer   "job_id"
      t.datetime "created_at"
      t.text      "message",         :limit => 1073741823
      t.string    "status"
      t.datetime  "last_alerted_at"
      t.datetime "updated_at"
    end

    add_index "job_errors", ["job_id"], :name => "job_id"

    create_table "jobs", :force => true do |t|
      t.datetime "created_at"
      t.datetime "updated_at"
      t.string    "name",                                            :null => false
      t.boolean   "active",                        :default => true, :null => false
      t.datetime  "last_run"
      t.string    "cron_expr",     :limit => 1024,                   :null => false
      t.string    "status"
      t.integer   "user_id"
      t.text      "alert_keys"
      t.datetime  "deleted_at"
      t.integer   "error_timeout",                 :default => 60,   :null => false
      t.datetime "next_run"
      t.text      "description"
      t.integer   "app_id",                                          :null => false
      t.text      "metrics",                                         :null => false
      t.text      "monitor_expr"
      t.integer   "minutes"
      t.text      "to_date"
    end

    add_index "jobs", ["app_id"], :name => "app_id"
    add_index "jobs", ["id", "name"], :name => "id_name_version_key", :unique => true
    add_index "jobs", ["user_id"], :name => "jobs_ibfk_1"

    create_table "users", :force => true do |t|
      t.datetime "created_at"
      t.datetime "updated_at"
      t.string    "encrypted_password", :null => false, :default => ""
      t.string    "email",       :null => false
      t.string    "first_name"
      t.string    "last_name"
      t.datetime "last_login"
      t.text      "preferences"
    end

    add_index "users", ["email"], :name => "email", :unique => true

  end
end
