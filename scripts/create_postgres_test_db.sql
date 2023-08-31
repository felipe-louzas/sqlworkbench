 -- superuser is required for the EventTrigger test
create user wbjunit 
    with superuser
    password 'wbjunit';
create database wbjunit
   encoding = 'UTF8'
   owner = wbjunit;

