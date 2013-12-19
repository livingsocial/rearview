class Rearview::User < ActiveRecord::Base
  include Rearview::Concerns::Models::User
  devise :omniauthable,:database_authenticatable,:omniauth_providers => [:google_oauth2]
  def self.valid_email?(email)
    email.present? && (email.ends_with?('hungrymachine.com')|| email.ends_with?('livingsocial.com'))
  end
end
