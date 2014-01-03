class Rearview::User < ActiveRecord::Base
  include Rearview::Concerns::Models::User
  devise :omniauthable,:database_authenticatable,:omniauth_providers => [:google_oauth2]
end
