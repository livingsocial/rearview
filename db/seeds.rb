admin = Rearview::User.new do |u|
  u.email = "admin@localhost"
  u.password = "admin"
end
admin.save!
