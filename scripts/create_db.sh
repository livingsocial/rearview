DB_USER=`grep "db.default.user" conf/common.conf | cut -d= -f2 | sed 's/"\([a-zA-Z0-9]*\)"/\1/'`
DB_PASSWORD=`grep "db.default.password" conf/common.conf | cut -d= -f2 | sed 's/"\(.*\)"/\1/'`

# dev database
mysql -u $DB_USER --password=$DB_PASSWORD -e "CREATE DATABASE IF NOT EXISTS rearview"
