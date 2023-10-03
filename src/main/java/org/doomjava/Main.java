package org.doomjava;

import de.bixilon.kotlinglm.mat3x3.Mat3;
import de.bixilon.kotlinglm.vec2.Vec2;
import de.bixilon.kotlinglm.vec3.Vec3;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.lang.Math.cos;
import static java.lang.Math.tan;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

class Thing {
    public int type, flags, angle;
    Vec2 pos = new Vec2();

    @Override
    public String toString() {
        return "Thing{" +
                "pos=" + pos +
                ", angle=" + angle +
                ", type=" + type +
                ", flags=" + flags +
                '}';
    }
}

class Vertex {
    public Vec2 pos;
    Vertex(int x, int y) { this.pos = new Vec2(x, y); }

    @Override
    public String toString() {
        return "Vertex{" +
                "pos=" + pos +
                '}';
    }
}

class LineDef {
    public int start_vertex, end_vertex, flags, sector_tag, special_type, front_sidedef, back_sidedef;

    @Override
    public String toString() {
        return "LineDef{" +
                "start_vertex=" + start_vertex +
                ", end_vertex=" + end_vertex +
                ", flags=" + flags +
                ", sector_tag=" + sector_tag +
                ", special_type=" + special_type +
                ", front_sidedef=" + front_sidedef +
                ", back_sidedef=" + back_sidedef +
                '}';
    }
}

class Segment {
    public int start_vertex, end_vertex, linedef, direction, offset, angle;

    @Override
    public String toString() {
        return "Segment{" +
                "start_vertex=" + start_vertex +
                ", end_vertex=" + end_vertex +
                ", angle=" + angle +
                ", linedef=" + linedef +
                ", direction=" + direction +
                ", offset=" + offset +
                '}';
    }
}

class Node {
    public int x, y, dx, dy, l_child, r_child;
    public int[] lbb = new int[4];
    public int[] rbb = new int[4]; // top -> bottom, left -> right

    @Override
    public String toString() {
        return "Node{" +
                "x=" + x +
                ", y=" + y +
                ", dx=" + dx +
                ", dy=" + dy +
                ", l_child=" + l_child +
                ", r_child=" + r_child +
                ", lbb=" + Arrays.toString(lbb) +
                ", rbb=" + Arrays.toString(rbb) +
                '}';
    }
}

class SubSector {
    public int count, fst_seg;

    @Override
    public String toString() {
        return "Subsector{" +
                "count=" + count +
                ", fst_seg=" + fst_seg +
                '}';
    }
}

class Sector {
    /*
    0	2	int16_t	Floor height
    2	2	int16_t	Ceiling height
    4	8	int8_t[8]	Name of floor texture
    12	8	int8_t[8]	Name of ceiling texture
    20	1	uint8_t	Light level
    21	1	uint8_t	Color ID
    22	2	int16_t	Special Type
    24	2	int16_t	Tag number
    26	2	int16_t	Flags
     */
    int floor_height, ceiling_height, special_type, tag_number, flags, light_level, color_ID;
    String floor_texture_name, ceiling_texture_name;
}

class Episode {
    public List<Vertex> vertices = new ArrayList<>();
    public List<LineDef> lines = new ArrayList<>();
    public List<Segment> segs = new ArrayList<>();
    public List<SubSector> ssectors = new ArrayList<>();
    public List<Sector> sectors = new ArrayList<>();;
    public List<Node> nodes = new ArrayList<>();
    public List<Thing> things = new ArrayList<>();

    @Override
    public String toString() {
        return "Episode{" +
                "vertices=" + vertices +
                ", lines=" + lines +
                ", segs=" + segs +
                ", ssectors=" + ssectors +
                ", nodes=" + nodes +
                '}';
    }
}

class Doom {
    final double PI = 3.1415926536;
    final double RAD_TO_DEG = 180 / PI;
    final double DEG_TO_RAD = 1 / RAD_TO_DEG;

    // The window handle
    private long window;

    int W_MAX = 1080, H_MAX = 1080;
    int fov = 90;
    List<Episode> eps = new ArrayList<>();

    int current_episode = 0;

    void renderWall(int x1, int x2, int y01, int yf1, int y02, int yf2) {
        for (int i = x1; i < x2; i+= 1) {
            // TODO
            var y0 = (int) ((x2 - i) / (float) (x2 - x1) * (y02 - y01));
            var yf = (int) ((x2 - i) / (float) (x2 - x1) * (yf2 - yf1));
//            System.out.printf("%d %d %d\n", i, y0, yf);

            glBegin(GL_LINES);
            glPointSize(1);
            glVertex2f((float) i / W_MAX, (float) y0 / H_MAX);
            glVertex2f((float) i / W_MAX, (float) (y0 + yf) / H_MAX);
            glEnd();

//            for (int j = y0; j < yf + y0; j++) {
//                glBegin(GL_LINES);
//                glPointSize(1);
//                glVertex2f((float) i / W_MAX, (float) j / H_MAX);
//                glVertex2f((float) i / W_MAX, (float) j / H_MAX);
//                glEnd();
//            }
        }
    }

    Vec3 colorFromHashCode(int hashcode) {
        Random random = new Random(hashcode);
        int red = random.nextInt(255);
        int green = random.nextInt(255);
        int blue = random.nextInt(255);
        return new Vec3(red,green,blue).div(255.f);
    }



//    int world_to_screen(int world_x, int player_x, int angle, int screen_width){
//
//        // dist de la player la world coordinate
//        int dist = screen_width / 2 * tan(angle/2);
//        //probabil mai trebuie ceva pe langa angle
//        return (screen_width/2) - (dist * tan(angle);
//
//    }

    int angleToScreenspaceX(int angle) {
        // -45 gr -> 0, 45 -> W_MAX
        angle = Math.min(fov / 2, Math.max(-fov / 2, angle));
        return (int) (cos((angle - fov / 2.f) * PI / 180.0f) * W_MAX);
    }

    boolean isOnLeftSide(int x, int dx, int y, int dy, int xc, int yc) {
        var deltax = xc - x;
        var deltay = yc - y;
        return deltax * dy - dx * deltay <= 0;
    }
    public void renderSeg(int seg_idx, Thing thing) {
        var segment = eps.get(current_episode).segs.get(seg_idx);

        var v1 = eps.get(current_episode).vertices.get(segment.start_vertex);
        var v2 = eps.get(current_episode).vertices.get(segment.end_vertex);

        var p1 = new Vec2(v1.pos).plus(-thing.pos.getX(), -thing.pos.getY()).div(W_MAX, H_MAX);
        var p2 = new Vec2(v2.pos).plus(-thing.pos.getX(), -thing.pos.getY()).div(W_MAX, H_MAX);

        var linedef = eps.get(current_episode).lines.get(segment.linedef);
        var sector = eps.get(current_episode).sectors.get(linedef.sector_tag);

//        var z2 = sector.ceiling_height;
//        var z1 = sector.floor_height;
//        var x1 = v1.pos.x;
//        var x2 = v2.pos.x;
//        var y1 = v1.pos.y;
//        var y2 = v2.pos.y;
//        int angle = segment.angle;

        var pangle = -((short)thing.angle * 360) / 256.f;

        var dir3 = new Mat3().rotateZ((float) (pangle * DEG_TO_RAD)).times(new Vec3(0, 1, 1));
        var dir2 = new Vec2(dir3.getX(), dir3.getY()).normalize();

        var v11 = v1.pos.minus(thing.pos);
        var v22 = v2.pos.minus(thing.pos);

        var angle1 = (int) (Math.acos(v11.dot(dir2) / v11.length() / dir2.length()) * RAD_TO_DEG);
        var angle2 = (int) (Math.acos(v22.dot(dir2) / v22.length() / dir2.length()) * RAD_TO_DEG);

        var x1 = angleToScreenspaceX(-angle1);
        var x2 = angleToScreenspaceX(angle2);

        var color = colorFromHashCode(segment.hashCode());

        if (angle2 < 45 || angle1 < 45) {
            var y01 = sector.floor_height;
            var y02 = sector.floor_height;
            var yf1 = sector.ceiling_height;
            var yf2 = sector.ceiling_height;

            var d1 = v11.length();
            var d2 = v22.length();

            // TODO world to screen + clamping
            System.out.println(y01 + " " + yf1 + " " + y02 + " " + yf2);

            renderWall(x1, x2, y01, yf1, y02, yf2);

            glColor3f(color.getX(), color.getY(), color.getZ());
        } else {
            glColor3f(0.1f, 0.1f, 0.1f);
        }
        glBegin(GL_LINES);
        glVertex2f(p1.getX(), p1.getY());
        glVertex2f(p2.getX(), p2.getY());
        glEnd();


    }
    public void renderNode(int node_idx, Thing thing) {
        final short IS_SUBSECTOR = (short) 0b1000000000000000;
        if ((node_idx & IS_SUBSECTOR) != 0) {
            // subsector
            node_idx = node_idx & (~IS_SUBSECTOR);
            SubSector ss = eps.get(current_episode).ssectors.get(node_idx);
            for (int seg_idx = 0; seg_idx < ss.count; seg_idx++) {
                renderSeg(ss.fst_seg + seg_idx, thing);
            }
        } else {
            // bsp node
            Node n = eps.get(current_episode).nodes.get(node_idx);
            if (isOnLeftSide(n.x, n.dx, n.y, n.dy, (int) thing.pos.getX(), (int) thing.pos.getY())) {
                renderNode(n.l_child, thing);
                renderNode(n.r_child, thing);
            } else {
                renderNode(n.r_child, thing);
                renderNode(n.l_child, thing);
            }
        }
    }
    public static int BAM2Degrees(int angle) {
        return (int) ((angle * 360) / 256.f);
    }
    public static int GetInt32(byte[] buffer) {
        return (buffer[3] & 0xFF) << 24 | (buffer[2] & 0xFF) << 16 | (buffer[1] & 0xFF) << 8 | (buffer[0] & 0xFF);
    }
    public static int GetInt16(byte[] buffer) {
        return (short) ((buffer[1] & 0xFF) << 8 | (buffer[0] & 0xFF));
    }
    private static int GetInt8(byte[] bytes) {
        return bytes[0];
    }
    private void readWAD(String filename) throws IOException {
        File file = new File(filename);
        var is = new FileInputStream(file);

        var type = new String(is.readNBytes(4), StandardCharsets.UTF_8);
        var lumpCount = GetInt32(is.readNBytes(4));
        var directoryStart = GetInt32(is.readNBytes(4));

        var lumps_bytes = is.readNBytes(directoryStart - 12);
        var directory_bytes = is.readAllBytes();

        var episodes = new ArrayList<Episode>();

        for (int i = 0; i < lumpCount; i += 16) {
            var location = GetInt32(Arrays.copyOfRange(directory_bytes, i, i + 4));
            var size = GetInt32(Arrays.copyOfRange(directory_bytes, i + 4, i + 8));
            var name = new String(Arrays.copyOfRange(directory_bytes, i + 8, i + 16), StandardCharsets.UTF_8);
//            System.out.printf("%d %s %d %d%n", i / 16, name, location, size);
            if (name.charAt(0) == 'E' && name.charAt(2) == 'M' && name.charAt(4) == NULL) {
                // episode start
                episodes.add(new Episode());
            } else if (name.equals("VERTEXES")) {
                for (int vert = 0; vert < size; vert += 4) {
                    var offset = - 12 + location + vert;

                    var x = GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2));
                    var y = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4));
                    var v = new Vertex(x, y);

                    episodes.get(episodes.size() - 1).vertices.add(v);
                }
            } else if (name.equals("LINEDEFS")) {
                for (int line = 0; line < size; line += 14) {
                    var offset = - 12 + location + line;

                    var linedef = new LineDef();
                    linedef.start_vertex    = GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2));
                    linedef.end_vertex      = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4));
                    linedef.flags           = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 4, offset + 6));
                    linedef.special_type    = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 6, offset + 8));
                    linedef.sector_tag      = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 8, offset + 10));
                    linedef.front_sidedef   = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 10, offset + 12));
                    linedef.back_sidedef    = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 12, offset + 14));

                    episodes.get(episodes.size() - 1).lines.add(linedef);
                }
            } else if (name.startsWith("SEGS")) {
                for (int seg = 0; seg < size; seg += 12) {
                    var offset = -12 + location + seg;

                    var segment = new Segment();
                    segment.start_vertex = GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2));
                    segment.end_vertex  = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4));
                    segment.angle       = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 4, offset + 6)) << 16;
                    segment.linedef     = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 6, offset + 8));
                    segment.direction   = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 8, offset + 10));
                    segment.offset      = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 10, offset + 12));

                    segment.angle = (int) ((segment.angle << 16) * 8.38190317e-8);
                    if (segment.angle < 0)
                        segment.angle = segment.angle + 360;

                    episodes.get(episodes.size() - 1).segs.add(segment);
                }
            } else if (name.equals("SSECTORS")) {
                for (int ssect = 0; ssect < size; ssect += 4) {
                    var offset = -12 + location + ssect;

                    var ssector = new SubSector();
                    ssector.count   = GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2));
                    ssector.fst_seg = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4));

                    episodes.get(episodes.size() - 1).ssectors.add(ssector);
                }
            } else if (name.startsWith("NODES")) {
                for (int node_idx = 0; node_idx < size; node_idx += 28) {
                    var offset = -12 + location + node_idx;

                    var node = new Node();
                    node.x      = GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2));
                    node.y      = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4));
                    node.dx     = GetInt16(Arrays.copyOfRange(lumps_bytes, offset+ 4, offset + 6));
                    node.dy     = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 6, offset + 8));
                    for (int i1 = 0; i1 < 4; i1++) {
                        node.rbb[i1] = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 8 + i1*2, offset + 10 + i1*2));
                        node.lbb[i1] = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 16 + i1*2, offset + 18 + i1*2));
                    }
                    node.r_child = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 24, offset + 26));
                    node.l_child = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 26, offset + 28));

                    episodes.get(episodes.size() - 1).nodes.add(node);
                }
            } else if (name.startsWith("THINGS")) {
                 for (int th = 0; th < size; th += 10) {
                     var offset = -12 + location + th;

                     var thing = new Thing();
                     thing.pos.setX(GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2)));
                     thing.pos.setY(GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4)));
                     thing.angle    = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 4, offset + 6)) << 16;
                     thing.type     = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 6, offset + 8));
                     thing.flags    = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 8, offset + 10));

                     episodes.get(episodes.size() - 1).things.add(thing);
                 }
            } else if (name.startsWith("SECTORS")) {
                for (int sect = 0; sect < size; sect += 28) {
                    var offset = -12 + location + sect;

                    var sector = new Sector();

                    sector.floor_height         = GetInt16(Arrays.copyOfRange(lumps_bytes, offset, offset + 2));
                    sector.ceiling_height       = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 2, offset + 4));
                    sector.floor_texture_name   = new String(Arrays.copyOfRange(directory_bytes, i + 4, i + 12), StandardCharsets.UTF_8);
                    sector.ceiling_texture_name = new String(Arrays.copyOfRange(directory_bytes, i + 12, i + 20), StandardCharsets.UTF_8);
                    sector.light_level          = GetInt8(Arrays.copyOfRange(lumps_bytes, offset + 20, offset + 21));
                    sector.color_ID             = GetInt8(Arrays.copyOfRange(lumps_bytes, offset + 20, offset + 22));
                    sector.special_type         = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 22, offset + 24));
                    sector.tag_number           = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 24, offset + 26));
                    sector.flags                = GetInt16(Arrays.copyOfRange(lumps_bytes, offset + 26, offset + 28));

                    episodes.get(episodes.size() - 1).sectors.add(sector);
                }
            }
            // TODO: read everything else
            // SIDEDEFS, REJECT?, BLOCKMAP?
            // https://doomwiki.org/wiki/Node
        }

        this.eps = episodes;

        is.close();
    }
    public void run() throws IOException {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        readWAD("DOOM.WAD");

        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
    private void init() {
        // Set up an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(W_MAX, H_MAX, "DOOM", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Set up a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }
    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();


        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        var player = eps.get(current_episode).things.stream().filter(thing -> thing.type == 1).toList().get(0);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) player.pos.plusAssign(0, 10);
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) player.pos.plusAssign(-10, 0);;
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) player.pos.plusAssign(0, -10);
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) player.pos.plusAssign(10, 0);

            if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) player.angle -= 1;
            if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) player.angle += 1;



            glColor3f(0, 1, 1);
            glPointSize(8);
            glBegin(GL_POINTS);
            glVertex2f(0,0);
            glEnd();


            var angle = - BAM2Degrees(player.angle);
            var p1 = new Mat3().rotateZ((float) ((135 + angle) * DEG_TO_RAD)).times(new Vec3(0.5, 0, 1));
            var p2 = new Mat3().rotateZ((float) ((45  + angle) * DEG_TO_RAD)).times(new Vec3(0.5, 0, 1));
//            System.out.println(angle);
//            System.out.println(p1);
//            System.out.println(p2);
            glBegin(GL_LINES);
            glVertex2f(0, 0);
            glVertex2f(p2.getX(), p2.getY() * W_MAX / H_MAX);
            glVertex2f(0, 0);
            glVertex2f(p1.getX(), p1.getY() * W_MAX / H_MAX);
            glEnd();

            var nodes = eps.get(current_episode).nodes;
            renderNode(nodes.size() - 1, player);

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
        }
    }

    public static void main(String[] args) throws IOException {
        new Doom().run();
    }
}