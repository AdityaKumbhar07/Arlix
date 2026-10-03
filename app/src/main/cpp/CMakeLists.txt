cmake_minimum_required(VERSION 3.22.1)
project("shadowvault_core")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(memory_sanitizer SHARED memory_sanitizer.cpp)

target_compile_options(memory_sanitizer PRIVATE -Wall -Wextra -O3)

# Align the library for 16 KB memory pages (needed by newer Android devices and Google Play).
target_link_options(memory_sanitizer PRIVATE -Wl,-z,max-page-size=16384)
