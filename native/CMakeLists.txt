cmake_minimum_required(VERSION 3.20)
project(market_data_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_library(itch_parser STATIC
    itch-parser/src/itch_parser.cpp
)
target_include_directories(itch_parser PUBLIC itch-parser/include)

add_library(replay_buffer STATIC
    replay-buffer/src/replay_buffer.cpp
)
target_include_directories(replay_buffer PUBLIC replay-buffer/include)
