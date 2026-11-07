cmake_minimum_required(VERSION 3.22.1)
project("spkup_jni")

# Configure C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define native JNI shared library
add_library(spkup_jni SHARED
    spkup_jni.cpp
)

# Link against Android logging
find_library(log-lib log)
target_link_libraries(spkup_jni
    ${log-lib}
)
