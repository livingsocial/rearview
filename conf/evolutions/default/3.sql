# --- !Ups

alter table job_errors add `status` enum('success','failed','error','graphite_error','graphite_metric_error') not null default 'failed';

# --- !Downs

alter table job_errors drop `status`;
