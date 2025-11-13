var exec = require('cordova/exec');

var Blufi = {
  events: function (onEvent, onError) {
    exec(onEvent, onError || function(){}, 'Blufi', 'events', []);
  },
  scan: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'scan', []);
  },
  stopScan: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'stopScan', []);
  },
  connect: function (address, success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'connect', [address]);
  },
  negotiateSecurity: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'negotiateSecurity', []);
  },
  configureSta: function (ssid, password, success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'configureSta', [ssid, password]);
  },
  deviceStatus: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'deviceStatus', []);
  },
  deviceVersion: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'deviceVersion', []);
  },
  deviceWifiScan: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'deviceWifiScan', []);
  },
  disconnect: function (success, fail) {
    exec(success || function(){}, fail || function(){}, 'Blufi', 'disconnect', []);
  }
};

module.exports = Blufi;
