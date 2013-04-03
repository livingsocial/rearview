# --- !Ups

delete from job_errors;
update jobs set status='success';

# --- !Downs

