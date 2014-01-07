# This migration comes from rearview (originally 20140106161202)
class AddIndexes < ActiveRecord::Migration
  def change
    add_index :jobs, :status
    add_index :job_errors, :status
    add_index :job_data, :job_id
  end
end
