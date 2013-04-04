define([
    'jquery',     
    'underscore', 
    'backbone',
    'handlebars',
    'timeline',
    'view/base',
    'backbone-mediator',
    'xdate'
], function(
    $, 
    _, 
    Backbone,
    Handlebars,
    Timeline,
    BaseView
){  

    var AlertTimelineView = BaseView.extend({

        events : {
            'show .accordion-body'   : 'preventOverflow',
            'shown .accordion-body'  : 'publishTimelineHeight',
            'hidden .accordion-body' : 'publishTimelineHeight'
        },

        subscriptions : {
            'view:dashboard:complete'  : 'render'
        },

        initialize : function(options) {
            var self = this;
            _.bindAll(this);

            self.templar      = options.templar;
            self.appId        = options.appId;
            self.status       = options.status;
            self.popovers     = [];
            self.openPopovers = [];
        },

        render : function() {
            var self = this;

            self.templar.render({
                path   : 'alerttimeline',
                el     : self.$el,
                data   : {}
            });

            self.$timeline  = self.$el.find('.alert-timeline .accordion-inner');
            self.$accordion = self.$el.find('.alert-timeline .accordion-body');

            self.setupAlertTimeline();
            //
            if (self.status) {
                self.setAlertStatus();
            }
        },

        setupAlertTimeline : function() {
            var self            = this,
                alertData       = [],
                jobAlertIdList  = [];

            $.ajax('/applications/' + self.appId + '/errors', {
                success : function(result) {
                    _.each(result, function(alert) {

                        _.extend(alert, {
                            'name' : ( typeof self.collection.get(alert.jobId) != 'undefined' ) ? self.collection.get(alert.jobId).get('name') : ''
                        });

                        alertData.push({
                            'id'      : alert.id,
                            'jobId'   : alert.jobId,
                            'start'   : new XDate(alert.date, true),
                            'end'     : new XDate(alert.endDate, true),
                            'status'  : alert.status,
                            'content' : alert.name //
                        });

                        jobAlertIdList.push(alert.jobId);
                    });
                    
                    self.timeline  = new Timeline(self.$timeline[0]);

                    // default timeline view to current time spanning 1day back and 0.5day into the future
                    var start = new XDate(Date.now() - 2 * 24/2 * 60 * 60 * 1000, true),
                        end   = new XDate(Date.now() + 1 * 24/2 * 60 * 60 * 1000, true);

                    self.timeline.draw(alertData, {
                        'utc'         : true,
                        'start'       : start,
                        'end'         : end,
                        'width'       : '100%',
                        'editable'    : false,
                        'style'       : 'box',
                        'intervalMin' : 1000 * 60 * 10,          // 10 seconds
                        'intervalMax' : 1000 * 60 * 60 * 24 * 3, // 3 day max zoom out
                    });

                    self.$timeline.on('mouseenter', function() {
                        $(window).on('scroll', self.stopBrowserScroll);
                    });
                    self.$timeline.on('mouseleave', function() {
                        $(window).off('scroll', self.stopBrowserScroll);
                    });

                    self.jobAlertList = jobAlertIdList;
                    self.addPopover();
                }
            });        
        }, 

        preventOverflow : function() {
            $(document.body).css('overflow','hidden');
        },

        publishTimelineHeight : function() {
            var self = this;

            // hide overflow at the beginning of transition
            // to keep a vertical scrollbar from appearing
            Backbone.Mediator.pub('view:alerttimeline:toggle', self.$accordion.height());
            $(document.body).css('overflow','auto');
        },

        setAlertStatus : function() {
            var self = this;

            self.$el.find('.icon-bell-alt').addClass('alert-status');
            self.$accordion.collapse('show');
        },

        stopBrowserScroll : function() {
            window.scrollTo(0,0);
        },

        addPopover : function() {
            var self = this;
            jobAlertIdList = _.uniq(self.jobAlertList);

            for (var i = jobAlertIdList.length - 1; i >= 0; i--) {
                
                var initPopover = function(jobId) {

                    var $content  = $("<div class='timeline-monitor'>").append($('#smallMonitor' + jobAlertIdList[i]).clone()),
                        $closeBtn = $("<button/>", {
                            'text'  : "Troubleshoot Monitor",
                            'class' : 'btn btn-inverse'
                        });

                    var $group = $('.timeline-group-' + jobId);

                    var el = $group.popover({
                        trigger   : 'manual',
                        html      : true,
                        placement : 'bottom',
                        delay     : { show : 100, hide : 200 },
                        container : 'body',
                        content   : $content
                    }).click(function(e) {
                        e.stopPropagation();
                        var $this = $(this);

                        _.each(self.openPopovers, function(openPopover) {
                            openPopover.data('popover').hide();
                            openPopover.data('popover').tip().removeClass('active');
                        });

                        self.openPopovers = [];
                        
                        // check that popover is active
                        if ($this.toggleClass('active').hasClass('active')) {
                            $closeBtn.off('click');
                            $('body').off('click');
                            $this.popover('show');

                            //
                            $closeBtn.on('click', function(e) {
                                e.stopPropagation();
                                $this.popover('hide');
                                $this.toggleClass('active');
                                $closeBtn.off('click');
                                self.openPopovers = [];

                                Backbone.Mediator.pub('alerttimeline:view:troubleshoot', jobId);
                            });

                            // scope append to current popover el
                            $this.data('popover').tip().append($closeBtn);
                            //
                            self.openPopovers.push($group);
                            //
                            $('body').on('click', function() {
                                self.openPopovers = [];
                                $this.removeClass('active');
                                $this.popover('hide');
                            });
                        } else {
                            self.openPopovers = [];
                            $this.popover('hide');
                        }
                    });
                }
                // initialize popovers with correct jobId reference
                initPopover(jobAlertIdList[i]);
            };
        },

        destructor : function() { console.info('alerttimeline:destructor()');
            var self     = this,
                parentEl = self.$el.prev();

            // unsubscribe from mediator channels
            self.destroySubscriptions();

            self.remove();
            self.unbind();
            self.off();

            self.$el.empty();

            // place the containing element back in the page for later
            $("<section class='timeline-wrap clearfix'></section>").insertAfter(parentEl);            
        }
    });

    return AlertTimelineView;
});