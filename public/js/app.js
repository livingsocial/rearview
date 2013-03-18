define([
    'jquery',
    'underscore',
    'backbone',
    'util/templar',
    'route/index',
    'view/alert',
    'view/primarynav',
    'view/header',
    'view/addmonitor',
    'view/addapplication',
    'view/dashboard',
    'view/ecosystem',
    'view/secondarynav',
    'model/user',
    'model/application',
    'collection/job',
    'collection/application',
    'backbone-mediator',
    'jquery-timepicker',
    'bootstrap'
], function(
    $,
    _,
    Backbone,
    Templar,
    IndexRouter,
    AlertView,
    PrimaryNavView,
    HeaderView,
    AddMonitorView,
    AddApplicationView,
    DashboardView,
    EcosystemView,
    SecondaryNavView,
    UserModel,
    ApplicationModel,
    JobCollection,
    ApplicationCollection
){
    /**
     * Controller
     *
     * This is the central location that sets up, modifies data, and decides
     * the next view state.  It also initializes and authenticates for the entire
     * application currently.
     **/
    var Controller = function() {};

    _.extend(Controller, {

        initialize : function() {
            var self = this;
            _.bindAll(self);

            // check authorization
            self.auth();

            // these views are evergreen, so on initialize of the app this is alright
            self.alert();
            self.primaryNav();
            self.header();
            self.secondaryNav();

            // indicate initialized application
            // and start history object
            Backbone.Mediator.pub('controller:app:init');
            self.controllerInit = true;
            Backbone.history.start();
        },

        /* Initialize Driven */

        alert : function() {
            var self = this;
            self.auth();
            self.alertView = new AlertView({
                'el'      : $('.alert-wrap'),
                'templar' : self.templar
            });
        },

        header : function() {
            var self = this;
            self.auth();
            new HeaderView({
                'el'      : $('.header-wrap'),
                'templar' : self.templar
            }).render();
        },

        primaryNav : function() {
            var self = this;
            self.auth();
            new PrimaryNavView({
                'el'         : $('.primary-nav-wrap'),
                'collection' : self.applicationCollection,
                'user'       : self.user,
                'templar'    : self.templar
            }).render();
        },

        secondaryNav : function() {
            var self  = this;
            self.auth();
            new SecondaryNavView({
                'el'      : $('.secondary-nav-wrap'),
                'templar' : self.templar
            }).render();
        },

        /* Router Driven */

        dashboard : function(appId, monitorId) {
            var self   = this,
                views  = [];

            self.auth();
            self.destroyViews();

            var jobCollection = new JobCollection(null, {
                appId : appId
            });

            views.push(new DashboardView({
                'el'         : $('.monitor-panel'),
                'collection' : jobCollection,
                'appId'      : appId,
                'user'       : self.user,
                'templar'    : self.templar,
                'router'     : self.indexRouter
            }));

            self.renderViews(views);

            Backbone.Mediator.pub('controller:dashboard:init', {
                'nav' : {
                    'dashboard' : true
                },
                'monitorId' : monitorId,
                'appId'     : appId
            });
        },

        ecosystem : function() {
            var self  = this,
                views = [];

            self.auth();

            Backbone.Mediator.pub('controller:dashboard:init', {
                'nav' : {
                    'dashboard' : false
                }
            });

            self.destroyViews();

            views.push(new EcosystemView({
                'el'         : $('.ecosystem-application-wrap'),
                'collection' : self.applicationCollection,
                'templar'    : self.templar
            }));

            var applicationModel = new ApplicationModel();

            views.push(new AddApplicationView({
                'el'         : $('.add-application-wrap'),
                'templar'    : self.templar,
                'model'      : applicationModel,
                'collection' : self.applicationCollection,
                'user'       : self.user
            }));

            self.renderViews(views);
        },

        /* Helper Methods */

        /**
         * Controller#destroyViews()
         *
         * Breaks down existing views from the previous route by calling their
         * destructor methods.  Each view is in charge of it's own memory
         * management and it is up to a new route to effectively clean up existing
         * views
         **/
        destroyViews : function() {
            var self = this;

            if (self.currentView) {
                while (self.currentView.length > 0) {
                    self.currentView.pop().destructor();
                }
            }
        },
        /**
         * Controller#renderViews(views)
         * - views (Array): list of view references
         *
         * This method handles rendering all views that were initialized in
         * the current route/controller
         **/
        renderViews : function(views) {
            var self = this;

            // set current views, so we can properly clean up our previous views
            // on the next destroyViews() execution
            self.currentView = views;

            for (var i = self.currentView.length - 1; i >= 0; i--) {
                self.currentView[i].render();
            }
        },
        /**
         * Controller#auth()
         *
         * Handles initial application load by checking that neccesary items
         * are addressed before anything else liek their is a user, router is
         * set up, etc.
         **/
        auth : function() {
            var self = this;

            // set reference to router in our application
            self.indexRouter = ( !self.indexRouter )
                             ? new IndexRouter({
                                 'app' : self
                               })
                             : self.indexRouter;

            self.user = ( !self.user )
                      ? new UserModel()
                      : self.user;

            self.applicationCollection = ( !self.applicationCollection )
                                       ? new ApplicationCollection()
                                       : self.applicationCollection;

            // using a front end handlebars template manager
            // which can handle caching in production and template
            // invalidation through versioning
            self.templar = ( !self.templar )
                         ? new Templar([
                               'addapplication',
                               'addmonitor',
                               'alert',
                               'application',
                               'deletemonitor',
                               'expandedmonitor',
                               'header',
                               'primarynav',
                               'schedulemonitor',
                               'secondarynav',
                               'setmetrics',
                               'smallmonitor'
                           ], {
                               version : ( !_.isUndefined(rearview.version) ) ? rearview.version : '0.0.1',
                               cache   : ( !_.isUndefined(rearview.version) && ( rearview.version.indexOf('dev') === -1 ) ) ? true : false
                           })
                         : self.templar;
        }
    });

    return Controller;
});
