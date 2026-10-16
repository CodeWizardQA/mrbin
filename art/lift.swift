// Lifts Mr. Bin out of the artwork with the subject lifting of macOS (Vision, macOS 14+) and writes the mask that
// art/icon.py uses. Run it again only if the artwork changes (two runs differ slightly at the soft edges):
//   swift art/lift.swift art/source/mrbin-art.jpg art/source/mrbin-mask.png
import CoreImage
import Foundation
import Vision

let args = CommandLine.arguments
guard args.count == 3, let image = CIImage(contentsOf: URL(fileURLWithPath: args[1])) else {
    FileHandle.standardError.write("usage: swift lift.swift <artwork.jpg> <mask.png>\n".data(using: .utf8)!)
    exit(1)
}
let handler = VNImageRequestHandler(ciImage: image)
let request = VNGenerateForegroundInstanceMaskRequest()
try handler.perform([request])
guard let result = request.results?.first else {
    FileHandle.standardError.write("no subject found\n".data(using: .utf8)!)
    exit(1)
}
let mask = CIImage(cvPixelBuffer: try result.generateScaledMaskForImage(forInstances: result.allInstances, from: handler))
try CIContext().writePNGRepresentation(of: mask, to: URL(fileURLWithPath: args[2]), format: .L8,
                                       colorSpace: CGColorSpaceCreateDeviceGray())
print("instances: \(result.allInstances.count), mask: \(args[2])")
