'use strict';

var syrup = require('syrup');
var gulp = require('gulp');

syrup.gulp.init(gulp, { compressJs: false, disableJsHint: true, detectGlobals: true }, undefined,
    {
      js: 'app/js/DictApp.js',
      less: 'app/css/*.less',
      allLess: 'app/css/**/*.less',
      build: '../public'
    }
  );