define(
[
    'underscore',
    'backbone',
    'backbone-mediator'
],
function(
    _, 
    Backbone
){

    /**
     * UserModel
     *
     * Generic model that represents a user in rearview.
     **/
    var UserModel = Backbone.Model.extend({
        
        url : '/user',

        defaults : {    
           id        : null,
           email     : '',
           firstName : '',
           lastName  : '',
           lastLogin : null
        },

        // On fetch success, publish event that user model is set.
        initialize : function() {
            var self = this;
            this.fetch({
                async   : false,
                success : function() {
                    Backbone.Mediator.pub('model:user:init', self);
                }
            });
        }
    });

    return UserModel;
});