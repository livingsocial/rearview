class Users::OmniauthCallbacksController < Devise::OmniauthCallbacksController
  skip_before_filter :authenticate_user!
  def google_oauth2
    raise "Google oauth2 prohibited" unless Rearview.config.authentication[:strategy] = :google_oauth2
    auth_info = request.env["omniauth.auth"].info
    unless Rearview::User.valid_google_oauth2_email?(auth_info['email'].to_s)
      return redirect_to(new_session_path, :flash => {
        :error => "Email #{auth_info['email'].to_s} is not authorized to access this application."
      })
    end
    user = Rearview::User.where(email: auth_info['email']).first_or_create
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

