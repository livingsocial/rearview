require.config({
    paths : {
        'backbone'              : '../vendor/backbone/js/backbone',
        'backbone-mediator'     : '../vendor/backbone/plugins/backbone-mediator/js/backbone-mediator',
        'backbone-localStorage' : '../vendor/backbone/plugins/backbone-localStorage/js/backbone-localStorage',
        'bootstrap'             : '../vendor/bootstrap/js/bootstrap',
        'bootstrap-datepicker'  : '../vendor/bootstrap/plugins/bootstrap-datepicker/js/bootstrap-datepicker',
        'codemirror'            : '../vendor/codemirror/lib/codemirror',
        'codemirror-ruby'       : '../vendor/codemirror/mode/ruby/ruby',
        'handlebars'            : '../vendor/handlebars/js/handlebars',
        'highcharts'            : '../vendor/highcharts/js/highcharts',
        'highcharts-gray'       : '../vendor/highcharts/js/themes/gray',
        'jquery'                : '../vendor/jquery/js/jquery',
        'jquery-validate'       : '../vendor/jquery/plugins/jquery.validate',
        'jquery-ui'             : '../vendor/jquery/plugins/jquery-ui/js/jquery-ui-1.9.2.custom.min',
        'jquery-timepicker'     : '../vendor/jquery/plugins/jquery.timepicker',
        'parsley'               : '../vendor/parsley/js/parsley.min',
        'underscore'            : '../vendor/underscore/js/underscore',
        'underscore-string'     : '../vendor/underscore/plugins/underscore-string/js/underscore.string',
        'xdate'                 : '../vendor/xdate/js/xdate',
        'timeline'              : '../vendor/timeline/timeline'
    },
    shim : {
        'backbone' : {
            deps    : ['underscore', 'jquery'],
            exports : 'Backbone'
        },
        'backbone-mediator' : {
            deps    : ['underscore', 'backbone']
        },
        'backbone-localStorage' : {
            deps    : ['backbone'],
            exports : 'Backbone.LocalStorage'
        },
        'bootstrap-datepicker' : {
            deps : ['bootstrap', 'jquery']
        },
        'codemirror' : {
            exports : 'CodeMirror'
        },
        'codemirror-ruby' : {
            deps : ['codemirror']
        },
        'handlebars' : {
            exports : 'Handlebars'
        },
        'highcharts' : {
            deps    : ['jquery'],
            exports : 'HighChart'
        },
        'highcharts-gray' : {
            deps : ['highcharts'],
            exports : 'HighChart'
        },
        'jquery' : {
            exports : '$'
        },
        'jquery-validate' : {
            deps : ['jquery']
        },
        'jquery-ui' : {
            deps : ['jquery']
        },
        'jquery-timepicker' : {
            deps : ['jquery-ui', 'jquery']
        },
        'parsley' : {
            deps    : ['jquery']
        },
        'timeline' : {
            exports : 'timeline'
        },
        'underscore' : {
            exports : '_'
        },
        'underscore-string' : {
            exports : '_.str'
        }
    }
});

require([
    // load our app controller
    'app'
], function(
    App
){
    App.initialize();
});