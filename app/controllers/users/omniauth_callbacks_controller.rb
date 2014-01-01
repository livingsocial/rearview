class Users::OmniauthCallbacksController < Devise::OmniauthCallbacksController
  skip_before_filter :authenticate_user!
  def google_oauth2
    auth_info = request.env["omniauth.auth"].info
    user = Rearview::User.find_or_create_by(:email,auth_info['email'])
    if user
      flash[:notice] = I18n.t("devise.omniauth_callbacks.success", :kind => "Google")
      session[:user_id] = user.id
      session[:access_token] = request.env["omniauth.auth"]["credentials"]["token"]
      sign_in_and_redirect(user, :event => :authentication)
    else
      flash[:error] = "An errror occured while trying to generate an account for you. Please contact an admin."
      session["devise.google_data"] = auth_info
      redirect_to new_session_path
    end
  end
end

