var gulp = require('gulp');
var webpack = require('webpack-stream');
var merge = require('merge2');
var replace = require('gulp-replace');
var clean = require('gulp-clean');
var sourcemaps = require('gulp-sourcemaps');
var argv = require('yargs').argv;
var fs = require('fs');

gulp.task('drop-cache', function(){
     return gulp.src(['./src/main/resources/public/dist'], { read: false })
		.pipe(clean());
});

gulp.task('webpack', ['drop-cache'], () => { 
    return gulp.src('./src/main/resources/public')
        .pipe(webpack(require('./webpack.config.js')))
        .on('error', function handleError() {
            this.emit('end'); // Recover from errors
        })
        .pipe(gulp.dest('./src/main/resources/public/dist'));
});
gulp.task('build', ['webpack'], () => {
    var refs = gulp.src("./src/main/resources/view-src/**/*.+(html|json)")
        .pipe(replace('@@VERSION', Date.now()))
        .pipe(gulp.dest("./src/main/resources/view"));

    var copyBehaviours = gulp.src('./src/main/resources/public/dist/+(behaviours|explorer.agent).js')
        .pipe(gulp.dest('./src/main/resources/public/js'));

    return merge[refs, copyBehaviours];
});

function getModName(fileContent){
    var getProp = function(prop){
        return fileContent.split(prop + '=')[1].split(/\r?\n/)[0];
    }
    return getProp('modowner') + '~' + getProp('modname') + '~' + getProp('version');
}

gulp.task('watch', () => {
    var springboard = argv.springboard;
    if(!springboard){
        springboard = '../springboard-open-ent/';
    }
    if(springboard[springboard.length - 1] !== '/'){
        springboard += '/';
    }

    gulp.watch('./src/main/resources/public/ts/**/*.ts', () => gulp.start('build'));

    fs.readFile("./gradle.properties", "utf8", function(error, content){
        var modName = getModName(content);
        gulp.watch(['./src/main/resources/public/template/**/*.html', '!./src/main/resources/public/template/entcore/*.html', './src/main/resources/public/sass/**/*.scss'], () => {
            console.log('Copying resources to ' + springboard + 'mods/' + modName);
            gulp.src('./src/main/resources/**/*')
                .pipe(gulp.dest(springboard + 'mods/' + modName));
        });

        gulp.watch('./src/main/resources/view/**/*.html', () => {
            console.log('Copying resources to ' + springboard + 'mods/' + modName);
            gulp.src('./src/main/resources/**/*')
                .pipe(gulp.dest(springboard + 'mods/' + modName));
        });
    });
});