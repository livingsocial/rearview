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
     * JobModel
     *
     * Generic model that represents a job (monitor) in rearview.
     **/

    // Example pay load to the rearview API
    // {
    //     "id"            : 1,
    //     "appId"         : 1,
    //     "userId"        : 1,
    //     "name"          : "test",
    //     "jobType"       : "monitor",
    //     "alertKeys"     : [""],
    //     "cronExpr"      : "0 * * * * ?",
    //     "errorTimeout"  : 60,
    //     "minutes"       : 1,
    //     "metrics"       : ["stats_counts.cupcake.subscription.create.queued"],
    //     "monitorExpr"   : "puts @a\nwith_metrics do |a|\n graph_value['queued', a]\nend\n\n",
    //     "active"        : true,
    //     "id"            : 1,
    //     "version"       : 1
    // }
    var JobModel = Backbone.Model.extend({
        url : function() {
            return ( this.get('id') ) ? '/jobs/' + this.get('id') : '/jobs';
        },
        defaults : {
            'id'            : null,
            'appId'         : 1,         // foreign key to application, temp global app = 1
            'userId'        : null,      // foreign key to user creator
            'name'          : '',
            'active'        : true,
            'jobType'       : 'monitor', // hard coded for now
            'version'       : 2,
            'alertKeys'     : [],
            'cronExpr'      : '',        // "0 * * * * ?"
            'errorTimeout'  : 60,        // hard coded for now, NOTE : not in design yet
            'minutes'       : 1,
            'metrics'       : [],
            'monitorExpr'   : '',
            'toDate'        : null
        }
    });

    return JobModel;
});
