@echo off

set version=%1
set action=%2

set initialized=false

if %version%=="" (
  echo Bitte version angeben!
  goto :eof
)

set pwfile=%~dp0password_for_postgres_db_user.txt
echo secret> %pwfile%
set PGPASSWORD=secret
set PGPORT=5445
set PGDATABASE=postgres
set PGHOST=localhost
SET PGUSER=postgres
set WB_TEST_PORT=5445

call :set_env %version%

if "%action%"=="stop" goto :stop_pg
if "%action%"=="init" goto :init_pg

call :prepare
call :start_pg

if %initialized%==true (
  %bindir%\psql -f %~dp0create_postgres_test_db.sql
)

if "%version%" == "8.4" (
  %bindir%\psql -U postgres -d wbjunit -f %~dp0create_plpgsql.sql
)

if "%action%"=="test" ( 
  call ant test-pg
  goto :eof  
)

if "%action%"=="testonce" (
  call ant test-pg
  call :stop_pg
  goto :eof
)

goto :eof

:set_env
  set pgdir=d:\etc\postgres-%1
  set datadir=d:\projects\sqlworkbench\testdata\pgdata\data_%1
  set bindir=%pgdir%\pgsql\bin
  rem echo %datadir%
  
  goto :eof
  
:prepare
   if not exist %datadir% (
      call :init_pg
   )
   goto :eof
   
:init_pg
   "%bindir%\initdb" -D "%datadir%" --lc-messages=English -U postgres --pwfile="%pwfile%" -E UTF8 -A md5 
   if not exist %datadir%\pg_log mkdir %datadir%\pg_log
   set initialized=true
   goto :eof

:start_pg
   if not exist %datadir%\postmaster.pid (
     start "Postgres" /D %pgdir% "%bindir%\pg_ctl" -l startup.log -D %datadir% start
     sleep 5
   )
   goto :eof
   
:stop_pg
   "%bindir%\pg_ctl" -D %datadir% stop
   goto :eof
