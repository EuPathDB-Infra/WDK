module.exports = function(grunt) {

  var wdkFiles = require('./wdkFiles');
  var helpers = require('./tasks/helpers');

  grunt.initConfig({

    jshint: {
      options: {
        jshintrc: true,
      },
      wdk: wdkFiles.src
    },

    concat: {
      js: {
        src: helpers.filterByFlag('env', 'prod', wdkFiles.libs),
        dest: 'dist/wdk/wdk.libs.js'
      }
    },

    handlebars: {
      compile: {
        options: {
          namespace: 'wdk.templates',
          processName: function(filePath) {
            return filePath.replace(/^src\/templates\//, '');
          }
        },
        files: {
          'dist/wdk/wdk.templates.js': ['src/templates/**/*.handlebars']
        }
      }
    },

    uglify: {
      options: {
        mangle: {
          except: ['wdk']
        },
        report: true,
        sourceMap: 'dist/wdk/wdk.js.map',
        sourceMappingURL: 'wdk.js.map',
        // sourceMapPrefix: 1
      },
      wdk: {
        files: {
          'dist/wdk/wdk.js': [].concat('dist/wdk/wdk.templates.js', wdkFiles.src),
        }
      }
    },

    cssmin: {
      wdk: {
        src: ['css/wdk.css'],
        dest: 'dist/wdk/css/wdk.min.css',
      },
      libs: {
        src: ['css/wdk.libs.css'],
        dest: 'dist/wdk/css/wdk.libs.min.css',
      }
    },

    copy: {
      js: {
        files: [
          {
            expand: true,
            //cwd: 'js',
            src: ['src/**', 'lib/**'],
            dest: 'dist/wdk'
          }
        ]
      },
      css: {
        files: [
          {
            expand: true,
            cwd: 'css',
            src: ['**/*'],
            dest: 'dist/wdk/css'
          }
        ]
      },
      images: {
        files: [
          {
            expand: true,
            cwd: 'images',
            src: ['**/*'],
            dest: 'dist/wdk/images'
          }
        ]
      }
    },

    clean: {
      dist: 'dist'
    }

  });

  grunt.loadTasks('tasks');
  grunt.loadNpmTasks('grunt-contrib-clean');
  grunt.loadNpmTasks('grunt-contrib-copy');
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-cssmin');
  grunt.loadNpmTasks('grunt-contrib-jshint');
  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-handlebars');

  grunt.registerTask('dist', ['jshint', 'clean', 'concat', 'handlebars', 'uglify', 'cssmin', 'copy', 'debugScript']);

  grunt.registerTask('default', ['dist']);

};

