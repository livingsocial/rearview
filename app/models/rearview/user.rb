class Rearview::User < ActiveRecord::Base
  include Rearview::Concerns::Models::User
  devise :database_authenticatable
end
