'use strict';

var gulp = require('gulp');
var browserify = require('gulp-browserify');
var gutil = require('gulp-util');
var util = require('util');
var path = require('path');
var syrup = require('syrup');
var Config = require('syrup').Config;

/**
 * Project configuration
 *
 * @property {string} env         The environment flag.  Valid values currently are 'dev', 'sbt-dev'
 *                                and 'prod'. Can be set by the environment variable NODE_ENV or
 *                                via the --env option. Defaults to 'dev'.
 *
 * @property {string} apiHost     The url of the API the front-end should use.  Can be set via the
 *                                NODE_API_HOST variable or the --api option.
 *
 * @property {string} paths.build The build directory where output resulting from the build should
 *                                be placed. Can be set via the NODE_BUILD_DIR environment variable
 *                                or the --builddir option. Defaults to the value of paths.build
 *                                in paths.js
 *
 */
var config = {
  env: process.env.NODE_ENV || gutil.env.env || 'dev',
  paths: require('./paths')
};

// Check for an override of the build directory, either from the environment variable NODE_BUILD_DIR
// or via the --target option.
var bd = process.env.NODE_BUILD_DIR || gutil.env.builddir;
if(bd) {
  config.paths.build = bd;
}
config = new Config(config);

// Print out the config
gutil.log(config.toString());

// Register default tasks, this creates the following tasks:
//   clean, html, less, js, assets, bundle-unit-tests, run-unit-tests, run-integration-tests,
//   start-server
syrup.gulp.tasks(gulp, config.paths, config.env);

gulp.task('js', ['clean'], function() {
   gutil.log(util.format('Compiling %s to %s',
          gutil.colors.magenta(config.paths.jsMain), gutil.colors.magenta(config.paths.build)));
      return gulp.src(config.paths.jsMain, { read: false })
        .pipe(browserify())
        .pipe(gulp.dest(config.paths.build));
});

// Watch and rebuild as is appropriate
gulp.task('watch', ['build'], function() {
  gulp.watch(config.paths.js, ['js', 'html']);
  gulp.watch(config.paths.allLess, ['less', 'html']);
  gulp.watch(config.paths.assets, ['assets', 'html']);
  gulp.watch(config.paths.html, ['html']);
});

// Setup our build task, which cleans, then copies assets, bundles js, compiles less
// and lastly copies html into the build-directory while cache-breaking resources.
gulp.task('build', ['clean', 'assets', 'js', 'less', 'html']);

// This is the task that gets executed when gulp is called without a task
gulp.task('default', ['build'])

