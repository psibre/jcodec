package org.jcodec.samples.transcode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Set;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.DumbRateControl;
import org.jcodec.codecs.h264.encode.H264FixedRateControl;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.prores.ProresDecoder;
import org.jcodec.codecs.prores.ProresToThumb2x2;
import org.jcodec.common.Codec;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.Format;
import org.jcodec.common.MuxerTrack;
import org.jcodec.common.VideoEncoder.EncodedFrame;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.common.model.Size;
import org.jcodec.common.tools.MainUtils;
import org.jcodec.common.tools.MainUtils.Cmd;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.containers.mp4.muxer.MP4Muxer;

class Prores2avc extends V2VTranscoder {
    private static final int DEFAULT_FIXED_BITS_PER_MB = 1024;
    private static final String FLAG_THUMBNAIL = "thumbnail";
    private static final String FLAG_RC = "rc";
    private static final String FLAG_BITS_PER_MB = "bitsPerMb";

    public static class Prores2avcTranscoder extends GenericTranscoder {
        private DemuxerTrack videoInputTrack;
        private MP4Muxer muxer;
        private MuxerTrack videoOutputTrack;
        private H264Encoder videoEncoder;
        private ProresDecoder videoDecoder;

        public Prores2avcTranscoder(Cmd cmd, Profile profile) {
            super(cmd, profile);
        }

        @Override
        protected void initDecode(SeekableByteChannel source) throws IOException {
            MP4Demuxer demux = new MP4Demuxer(source);
            videoInputTrack = demux.getVideoTrack();
            if (cmd.getBooleanFlagD(FLAG_THUMBNAIL, false)) {
                videoDecoder = new ProresToThumb2x2();
            } else {
                videoDecoder = new ProresDecoder();
            }
        }

        @Override
        protected void initEncode(SeekableByteChannel sink) throws IOException {
            muxer = MP4Muxer.createMP4Muxer(sink, Brand.MP4);
            videoOutputTrack = muxer.addVideoTrack(Codec.H264, videoInputTrack.getMeta().getVideoCodecMeta());
            String rcName = cmd.getStringFlagD(FLAG_RC, "dumb");
            RateControl rc;
            if ("dumb".equals(rcName)) {
                rc = new DumbRateControl();
            } else if ("fixed".equals(rcName)) {
                rc = new H264FixedRateControl(cmd.getIntegerFlagD(FLAG_BITS_PER_MB, DEFAULT_FIXED_BITS_PER_MB));
            } else {
                System.err.println("Unsupported rate control mode: " + rcName);
                return;
            }
            videoEncoder = new H264Encoder(rc);
            videoEncoder.setKeyInterval(25);
        }

        @Override
        protected void finishEncode() throws IOException {
            muxer.finish();
        }

        @Override
        protected Picture8Bit createPixelBuffer(ColorSpace colorspace, ByteBuffer firstFrame) {
            Size size = videoInputTrack.getMeta().getVideoCodecMeta().getSize();
            return Picture8Bit.create(size.getWidth(), size.getHeight(), colorspace);
        }

        @Override
        protected ColorSpace getEncoderColorspace() {
            return videoEncoder.getSupportedColorSpaces()[0];
        }

        @Override
        protected Packet inputVideoPacket() throws IOException {
            return videoInputTrack.nextFrame();
        }

        @Override
        protected void outputVideoPacket(Packet packet) throws IOException {
            videoOutputTrack.addFrame(packet);
        }

        @Override
        protected Picture8Bit decodeVideo(ByteBuffer data, Picture8Bit target1) {
            return videoDecoder.decodeFrame8Bit(data, target1.getData());
        }

        @Override
        protected EncodedFrame encodeVideo(Picture8Bit frame, ByteBuffer _out) {
            return videoEncoder.encodeFrame8Bit(frame, _out);
        }

        @Override
        protected boolean haveAudio() {
            return false;
        }

        @Override
        protected Packet inputAudioPacket() throws IOException {
            return null;
        }

        @Override
        protected void outputAudioPacket(Packet audioPkt) throws IOException {
        }

        @Override
        protected ByteBuffer decodeAudio(ByteBuffer audioPkt) throws IOException {
            return null;
        }

        @Override
        protected ByteBuffer encodeAudio(ByteBuffer wrap) {
            return null;
        }

        @Override
        protected boolean seek(int frame) throws IOException {
            return false;
        }

        @Override
        protected int getBufferSize(Picture8Bit frame) {
            // Assume 4x min compression for h.264
            return frame.getWidth() * frame.getHeight() / 4;
        }
    }

    @Override
    public void printHelp(PrintStream err) {
        MainUtils.printHelpVarArgs(new HashMap<String, String>() {
            {
                put(FLAG_RC, "Rate control algorythm");
                put(FLAG_THUMBNAIL, "Use ProRes thumbnail decoder");
            }
        }, "in file", "pattern");
    }

    @Override
    public Set<Format> inputFormat() {
        return TranscodeMain.formats(Format.MOV);
    }

    @Override
    public Set<Format> outputFormat() {
        return TranscodeMain.formats(Format.MOV);
    }

    @Override
    public Set<Codec> inputVideoCodec() {
        return TranscodeMain.codecs(Codec.PRORES);
    }

    @Override
    public Set<Codec> outputVideoCodec() {
        return TranscodeMain.codecs(Codec.H264);
    }

    @Override
    public Set<Codec> inputAudioCodec() {
        return null;
    }

    @Override
    public Set<Codec> outputAudioCodec() {
        return null;
    }

    @Override
    public GenericTranscoder getTranscoder(Cmd cmd, Profile profile) {
        return new Prores2avcTranscoder(cmd, profile);
    }
}