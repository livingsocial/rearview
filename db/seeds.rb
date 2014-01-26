unless Rearview::User.exists?(email: "admin@localhost")
  admin = Rearview::User.new do |u|
    u.email = "admin@localhost"
    u.password = "admin"
  end
  admin.save!
end
