RearviewWeb::Application.routes.draw do
  mount Rearview::Engine => "/"
  devise_for :users, class_name: "Rearview::User", module: :devise , controllers: { :omniauth_callbacks => "users/omniauth_callbacks" }
end
