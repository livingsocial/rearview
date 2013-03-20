# --- !Ups

alter table jobs modify `status` enum('success','failed','error','graphite_error','graphite_metric_error', 'security_error') DEFAULT NULL;

# --- !Downs

alter table jobs modify `status` enum('success','failed','error','graphite_error','graphite_metric_error') DEFAULT NULL;
