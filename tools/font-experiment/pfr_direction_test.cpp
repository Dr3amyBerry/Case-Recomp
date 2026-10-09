// Research regression harness for the external AGPL-3.0 PFR1 parser.
// Add the prepared private copy directory to the include path.
#include <Pfr1Font.cpp>
#include <iostream>
int main() {
    using libreshockwave::font::orusLookup;
    const std::vector<int> references{-20, 0, 60, 300, 650};
    // Same coordinate, different incoming tangent: next ORU must differ.
    if (orusLookup(references, 150, 0, 100) != 300) return 1;
    if (orusLookup(references, 150, 0, 200) != 60) return 2;
    if (orusLookup(references, 150, 0, 150) != 150) return 3;
    if (orusLookup(references, 60, 0, 0) != 300) return 4;
    if (orusLookup(references, 60, 0, 150) != 0) return 5;
    if (orusLookup(references, 150, 1, 200) != 300) return 6;
    if (orusLookup(references, 150, -1, 100) != 60) return 7;
    if (orusLookup({}, 150, 0, 100) != 150) return 8;
    std::cout << "PASS: implicit ascending/descending/equal, strict ORU, explicit direction, empty table\n";
}
