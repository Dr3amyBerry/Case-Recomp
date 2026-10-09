// Standalone adapter for an externally supplied LibreShockwave PFR1 parser.
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iomanip>
#include <limits>
#include <iterator>
#include <stdexcept>
#include <vector>
#include "libreshockwave/font/Pfr1Font.hpp"

int main(int argc, char** argv) {
    try {
        if (argc != 3) throw std::runtime_error("usage: pfr_convert input.pfr output.json");
        const auto size = std::filesystem::file_size(argv[1]);
        if (size < 58 || size > 1024 * 1024) throw std::runtime_error("invalid PFR size");
        if (std::filesystem::exists(argv[2])) throw std::runtime_error("output already exists");
        std::ifstream input(argv[1], std::ios::binary);
        if (!input) throw std::runtime_error("cannot open input");
        std::vector<std::uint8_t> data{std::istreambuf_iterator<char>(input), {}};
        auto font = libreshockwave::font::Pfr1Font::parse(data);
        if (!font || font->glyphs.empty()) throw std::runtime_error("no outline glyphs parsed");
        std::ofstream output(argv[2]);
        output << std::setprecision(std::numeric_limits<float>::max_digits10);
        const auto& metrics = font->metrics;
        output << "{\"units\":" << metrics.outlineResolution
               << ",\"metric_units\":" << metrics.metricsResolution
               << ",\"ascender\":" << metrics.ascender
               << ",\"descender\":" << metrics.descender << ",\"glyphs\":[";
        bool first_glyph = true;
        for (const auto& record : font->charRecords) {
            const auto found = font->glyphs.find(record.charCode);
            if (found == font->glyphs.end()) throw std::runtime_error("missing parsed glyph");
            if (!first_glyph) output << ',';
            first_glyph = false;
            output << "{\"code\":" << record.charCode << ",\"width\":" << record.setWidth
                   << ",\"gps_size\":" << record.gpsSize << ",\"contours\":[";
            bool first_contour = true;
            for (const auto& contour : found->second.contours) {
                if (!first_contour) output << ',';
                first_contour = false;
                output << '[';
                bool first_command = true;
                for (const auto& command : contour.commands) {
                    if (!first_command) output << ',';
                    first_command = false;
                    output << '[' << command.type << ',' << command.x << ',' << command.y
                           << ',' << command.x1 << ',' << command.y1
                           << ',' << command.x2 << ',' << command.y2 << ']';
                }
                output << ']';
            }
            output << "]}";
        }
        output << "]}";
        output.close();
        if (!output) throw std::runtime_error("cannot write output");
        std::cout << "records=" << font->charRecords.size() << " outlines=" << font->glyphs.size()
                  << " units=" << metrics.outlineResolution << '\n';
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
