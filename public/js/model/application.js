define(
[
    'underscore',
    'backbone'
],
function(
    _, 
    Backbone
){

    /**
     * ApplicationModel
     *
     * Generic model that represents a job (monitor) in rearview.
     **/
    var ApplicationModel = Backbone.Model.extend({
        url      : function() {
            return ( this.get('id') ) ? '/applications/' + this.get('id') : '/applications';
        },
        defaults : {
            'id'            : null,
            'userId'        : null,
            'name'          : 'Default'
        }
    });

    return ApplicationModel;
});