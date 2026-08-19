# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)
include_directories(LB/include)
# Declares and names the project.

project("sip2mqtt")
#set(APP_OPTIM "debug")
#set(APP_OPTIM "-O3")
#add_definitions(-DCMAKE_BUILD_TYPE=Debug)
set(CMAKE_BUILD_TYPE Debug)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g")

set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -g")
#SET(CMAKE_CXX_FLAGS_DEBUG "-g")
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.


add_library( # Sets the name of the library.
        sip2mqtt

        # Sets the library as a shared library.
        SHARED

        lbMain.cpp
        # Provides a relative path to your source file(s).
        LB/src/lb.c
        LB/src/lbLog.c)
target_compile_options(sip2mqtt PRIVATE -g)


#add_library(rntc
#        STATIC
#        IMPORTED)
#set_target_properties(rntc
#        PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}/librntc.a)
#${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libvcore_rntc.a
#set_property(TARGET rntc PROPERTY POSITION_INDEPENDENT_CODE ON)

#add_library(pjlib-util
#        STATIC
#        IMPORTED)
#set_target_properties(pjlib-util
#        PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_SOURCE_DIR}/../jniLibs/armeabi-v7a/libpjlib-util.a)
#
#add_library(pjnath
#        STATIC
#        IMPORTED)
#set_target_properties(pjnath
#        PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_SOURCE_DIR}/../jniLibs/armeabi-v7a/libpjnath.a)
#
#add_library(pj
#        STATIC
#        IMPORTED)
#set_target_properties(pj
#        PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_SOURCE_DIR}/../jniLibs/armeabi-v7a/libpj.a)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib
#
#        # Specifies the name of the NDK library that
#        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        sip2mqtt
#        rntc
#        pjnath
#        pjlib-util
#        pj
#        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        )