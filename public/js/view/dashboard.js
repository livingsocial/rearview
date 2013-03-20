define([
    'jquery',     
    'underscore', 
    'backbone',
    'handlebars',
    'model/job',
    'collection/job',
    'view/base',
    'view/smallmonitor',
    'view/editmonitor',
    'view/addmonitor',
    'codemirror',
    'highcharts',
    'highcharts-gray',
    'xdate',
    'codemirror-ruby',
    'jquery-validate',
    'backbone-mediator'
], function(
    $, 
    _, 
    Backbone,
    Handlebars,
    JobModel,
    JobCollection,
    BaseView,
    SmallMonitorView,
    EditMonitorView,
    AddMonitorView,
    CodeMirror,
    HighChart
){  
    var DashboardView = BaseView.extend({
        rowMonitorLimit : 3,
        monitors        : [],

        subscriptions : {
            'view:editmonitor:open'  : 'hideDash',
            'view:editmonitor:exit'  : 'showDash',
            'view:editmonitor:save'  : 'updateDash',
            'view:addmonitor:close'  : 'showDash',
            'view:addmonitor:save'   : 'updateDash',
            'view:addmonitor:show'   : 'hideDash',
            'view:smallmonitor:edit' : 'editMonitor'
        },

        initialize : function(options) {
            var self = this;
            _.bindAll(self);

            self.templar = options.templar;
            self.appId   = ( options.appId ) ? options.appId : 1;
            self.user    = ( options.user ) ? options.user : null;
            self.router  = ( options.router ) ? options.router : null;

            self.editMonitorView = new EditMonitorView({
                'el'      : $('.edit-monitor-wrap'),
                'user'    : self.user,
                'templar' : self.templar,
                'router'  : self.router
            });

            var jobModel = new JobModel();
            self.addMonitorView = new AddMonitorView({
                'model'   : jobModel,
                'user'    : self.user,
                'appId'   : self.appId,
                'templar' : self.templar
            });

            Backbone.Mediator.pub('view:dashboard:init');
        },

        render : function() {
            var self = this;
            self.initMonitors();
            return self;
        },
        /**
         * DashboardView#initMonitors()
         *                  
         * 
         **/
        initMonitors : function() {
            var self = this;

            self.getApplicationInfo(self.appId, function(title) {
                Backbone.Mediator.pub('view:dashboard:render', {
                    'title'    : title,
                    'subtitle' : 'Monitors Dashboard',
                    'nav'      : {
                        'ecosystem' : false,
                        'dashboard' : true
                    },
                    'appId'    : self.appId
                });
            });

            self.collection.each(function( monitor ) {
                self._updateMonitorList(monitor);
            });

            self.showDash();
        },

        getApplicationInfo : function(appId, cb) {
            var self = this;

            $.get('/applications/' + appId, function(data) {
                self.appName = data.name;
                
                if (typeof cb === 'function') {
                    cb(self.appName);
                }
            });
        },

        editMonitor : function(data) {
            var self = this;
            self.editMonitorView.render(data);
        },

        updateDash : function(data) {
            var self = this;
	    if (data.status && data.status != 'error') {
		self.collection = new JobCollection(null, {
                    appId : self.appId
                });

                // data is sent by pub/sub when actions are required
                // otherwise we just show/hide on the dashboard
                self.reinitializeDash(data);
            }
        },

        reinitializeDash : function(data) {
            var self = this;
            self.$el.empty();

            // start cleaning up monitor views
            self.destroyMonitors();
            
            // check for newly saved monitors and
            // place the 'waiting till next update' 
            // messaging on them till they update
            if (data) {
                self.updateSavedMonitorStatus(data);
            }

            // destroy nexted views and recreate them
            self.editMonitorView.destructor();
            self.editMonitorView = new EditMonitorView({
                'el'      : $('.edit-monitor-wrap'),
                'user'    : self.user,
                'templar' : self.templar,
                'router'  : self.router
            });

            // clean up nested views
            self.addMonitorView.destructor();
            var jobModel = new JobModel();
            self.addMonitorView = new AddMonitorView({
                'model'   : jobModel,
                'user'    : self.user,
                'appId'   : self.appId,
                'templar' : self.templar
            });

            // init monitors
            self.initMonitors();
        },

        hideDash : function() {
            var self = this;
            self.$el.hide();
        },

        showDash : function() {
            var self = this;
            self.$el.show();
            
            // highcharts gets stuck sometimes, firing a 
            // resize event keeps it from sticking
            $(window).trigger('resize');
        },

        updateSavedMonitorStatus : function(data) {
            var self  = this,
                model = ( data ) ? data.model : null;

            if ( model ) {
                _.each(self.monitors, function(view) {
                    if(view.model.get('id') === model.get('id')) {
                        view.nextRun();
                    }
                });
            }
        },
        /** internal
         * SmallMonitorView#_updateMonitorList(monitorEl)
         * - monitorEl (Object): DOM object reference to small monitor
         *  
         * This method is simply to place small monitors in bootstrap
         * rows and add them correctly.
         **/
        _updateMonitorList : function(monitor) {
            var self         = this,
                lastRowEl    = self.$el.find('.row-fluid:last'),
                monitorWrap  = $("<div class='small-monitor-wrap span4'></div>"),
                monitorCount = lastRowEl.find('.small-monitor').length;

            // handling first monitor case
            if(!lastRowEl.length) {
                self.$el.append("<ul class='monitor-grid'><li class='row-fluid'></li></ul>");
                lastRowEl = self.$el.find('.row-fluid:last');

                self.monitors.push(new SmallMonitorView({
                    'el'      : monitorWrap,
                    'model'   : monitor,
                    'appId'   : self.appId,
                    'templar' : self.templar
                }));
                lastRowEl.append(monitorWrap);

                self.monitors[self.monitors.length - 1].setElement(monitorWrap);
                self.monitors[self.monitors.length - 1].render();
            } else { // otherwise look for last row and make sure there are no more than 4 monitors inside
                if( monitorCount < self.rowMonitorLimit ) {

                    self.monitors.push(new SmallMonitorView({
                        'el'      : monitorWrap,
                        'model'   : monitor,
                        'appId'   : self.appId,
                        'templar' : self.templar
                    }));
                    lastRowEl.append(monitorWrap);

                    self.monitors[self.monitors.length - 1].setElement(monitorWrap);
                    self.monitors[self.monitors.length - 1].render();
                } else {
                    self.$el.find('.monitor-grid').append("<li class='row-fluid'></li>");
                    lastRowEl = self.$el.find('.row-fluid:last');

                    monitorWrap = $("<div class='small-monitor-wrap span4'></div>");
                    self.monitors.push(new SmallMonitorView({
                        'el'      : monitorWrap,
                        'model'   : monitor,
                        'appId'   : self.appId,
                        'templar' : self.templar
                    }));
                    lastRowEl.append(monitorWrap);

                    self.monitors[self.monitors.length - 1].setElement(monitorWrap);
                    self.monitors[self.monitors.length - 1].render();
                }
            }
            
        },

        destroyMonitors : function() {
            var self = this;

            for (viewName in self.monitors) {
                var view = self.monitors[viewName];
                view.destructor();
                delete self.monitors[viewName];
            }
        },

        destructor : function() {
            var self     = this,
                parentEl = self.$el.parent();

            // start cleaning up monitor views
            self.destroyMonitors();
            
            // unbind collection
            self.collection.off('remove', self.reinitializeDash, self);

            // unsubscribe from mediator channels
            Backbone.Mediator.unsubscribe('view:smallmonitor:edit', self.editMonitor, self);
            Backbone.Mediator.unsubscribe('view:editmonitor:open', self.hideDash, self);
            Backbone.Mediator.unsubscribe('view:editmonitor:exit', self.showDash, self);
            Backbone.Mediator.unsubscribe('view:editmonitor:save', self.updateDash, self);
            Backbone.Mediator.unsubscribe('view:addmonitor:close', self.showDash, self);
            Backbone.Mediator.unsubscribe('view:addmonitor:show', self.hideDash, self);
            Backbone.Mediator.unsubscribe('view:addmonitor:save', self.updateDash, self);

            // clean up edit/add monitor view
            self.editMonitorView.destructor();
            self.addMonitorView.destructor();
            
            self.remove();
            self.unbind();
            self.monitors = [];

            self.$el.empty();

            // place the containing element back in the page for later
            parentEl.prepend("<div class='monitor-panel container clearfix'></div>");     
        }
    });

    return DashboardView;
});
