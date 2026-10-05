import Foundation
import AVFoundation
import AppKit
import CoreVideo
let output = URL(fileURLWithPath: CommandLine.arguments[2])
try? FileManager.default.removeItem(at: output)
let image = NSImage(contentsOfFile: CommandLine.arguments[1])!
var rect = CGRect(x: 0,y: 0,width: 480,height: 640)
let cg = image.cgImage(forProposedRect: &rect, context: nil, hints: nil)!
let writer = try AVAssetWriter(outputURL: output, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 480, AVVideoHeightKey: 640, AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 600000]])
let adapter = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,kCVPixelBufferWidthKey as String: 480,kCVPixelBufferHeightKey as String: 640])
writer.add(input);writer.startWriting();writer.startSession(atSourceTime: .zero)
for index in 0..<72 {
 while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.005) }
 var buffer: CVPixelBuffer?;CVPixelBufferPoolCreatePixelBuffer(nil,adapter.pixelBufferPool!,&buffer)
 let pixel = buffer!;CVPixelBufferLockBaseAddress(pixel,[])
 let context = CGContext(data: CVPixelBufferGetBaseAddress(pixel),width: 480,height: 640,bitsPerComponent: 8,bytesPerRow: CVPixelBufferGetBytesPerRow(pixel),space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)!
 // This local test clip uses a gentle zoom of a PDF still, not the original effect animation.
 let baseScale = max(480.0 / Double(cg.width), 640.0 / Double(cg.height))
 let baseWidth = Double(cg.width) * baseScale
 let baseHeight = Double(cg.height) * baseScale
 let zoom = 1.0 + 0.025 * (1.0 - cos(Double(index) * 2.0 * .pi / 72))
 context.draw(cg,in: CGRect(x: (480 - baseWidth*zoom)/2,y: (640 - baseHeight*zoom)/2,width: baseWidth*zoom,height: baseHeight*zoom))
 CVPixelBufferUnlockBaseAddress(pixel,[])
 adapter.append(pixel,withPresentationTime: CMTime(value:Int64(index),timescale:24))
}
input.markAsFinished();let semaphore=DispatchSemaphore(value:0);writer.finishWriting { semaphore.signal() };semaphore.wait()
if writer.status != .completed { fatalError(String(describing:writer.error)) }
print("Local demo clip ready")
