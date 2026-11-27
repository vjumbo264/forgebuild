#!/usr/bin/env python3
"""Software GLES test runner. Takes Kotlin CcFinishingFixtures output.
Requires Mesa EGL, NumPy, Pillow and PyOpenGL. Does not measure phone performance.
"""
import os
os.environ.setdefault('PYOPENGL_PLATFORM', 'egl')
os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE', '1')
os.environ.setdefault("GALLIUM_DRIVER", "softpipe")
import ctypes
import sys
from pathlib import Path
import numpy as np
from PIL import Image
from OpenGL import EGL as E
from OpenGL import GL as G
from OpenGL.GL.shaders import compileShader, compileProgram


def init_gl():
    display = E.eglGetDisplay(E.EGL_DEFAULT_DISPLAY)
    E.eglInitialize(display, None, None)
    E.eglBindAPI(E.EGL_OPENGL_ES_API)
    attrib = [E.EGL_SURFACE_TYPE, E.EGL_PBUFFER_BIT, E.EGL_RENDERABLE_TYPE, E.EGL_OPENGL_ES2_BIT,
              E.EGL_RED_SIZE, 8, E.EGL_GREEN_SIZE, 8, E.EGL_BLUE_SIZE, 8, E.EGL_ALPHA_SIZE, 8, E.EGL_NONE]
    configs = (E.EGLConfig * 1)(); count = E.EGLint()
    assert E.eglChooseConfig(display, attrib, configs, 1, count) and count.value == 1
    surface = E.eglCreatePbufferSurface(display, configs[0], [E.EGL_WIDTH, 1, E.EGL_HEIGHT, 1, E.EGL_NONE])
    context = E.eglCreateContext(display, configs[0], E.EGL_NO_CONTEXT, [E.EGL_CONTEXT_CLIENT_VERSION, 2, E.EGL_NONE])
    E.eglMakeCurrent(display, surface, surface, context)
    G.glDisable(G.GL_DITHER)
    print(G.glGetString(G.GL_RENDERER).decode())


VERTEX = 'attribute vec2 aPos; varying vec2 vTex; void main(){vTex=aPos*.5+.5;gl_Position=vec4(aPos,0.,1.);}'


def texture(w, h, data=None, floating=False):
    tex = G.glGenTextures(1)
    G.glBindTexture(G.GL_TEXTURE_2D, tex)
    for key in (G.GL_TEXTURE_WRAP_S, G.GL_TEXTURE_WRAP_T):
        G.glTexParameteri(G.GL_TEXTURE_2D, key, G.GL_CLAMP_TO_EDGE)
    for key in (G.GL_TEXTURE_MIN_FILTER, G.GL_TEXTURE_MAG_FILTER):
        G.glTexParameteri(G.GL_TEXTURE_2D, key, G.GL_LINEAR)
    G.glTexImage2D(G.GL_TEXTURE_2D, 0, 0x881A if floating else G.GL_RGBA, w, h, 0, G.GL_RGBA,
                   G.GL_FLOAT if floating else G.GL_UNSIGNED_BYTE, data)
    return tex


def draw(prog, source, w, h, uniforms, previous=None, floating=False):
    dst = texture(w, h, floating=floating)
    G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, dst, 0)
    assert G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER) == G.GL_FRAMEBUFFER_COMPLETE
    G.glViewport(0, 0, w, h)
    G.glUseProgram(prog)
    for unit, name, tex in [(0, 'uTexture', source), (1, 'uPrev', previous)]:
        G.glActiveTexture(G.GL_TEXTURE0 + unit)
        G.glBindTexture(G.GL_TEXTURE_2D, tex or 0)
        G.glUniform1i(G.glGetUniformLocation(prog, name), unit)
    for key, value in uniforms.items():
        G.glUniform1f(G.glGetUniformLocation(prog, key), value)
    G.glUniform2f(G.glGetUniformLocation(prog, 'uTexelSize'), 1 / w, 1 / h)
    pos = G.glGetAttribLocation(prog, 'aPos')
    G.glEnableVertexAttribArray(pos)
    G.glVertexAttribPointer(pos, 2, G.GL_FLOAT, False, 0, ctypes.c_void_p(0))
    G.glDrawArrays(G.GL_TRIANGLES, 0, 3)
    return dst


def main(path):
    init_gl()
    source_dir = Path(__file__).resolve().parents[1] / 'core/src/main/resources/cc-studio'
    programs = {p.stem: compileProgram(compileShader(VERTEX, G.GL_VERTEX_SHADER),
                                    compileShader(p.read_text(), G.GL_FRAGMENT_SHADER)) for p in sorted(source_dir.glob('*.frag'))}
    G.glBindFramebuffer(G.GL_FRAMEBUFFER, G.glGenFramebuffers(1))
    vbo = G.glGenBuffers(1)
    G.glBindBuffer(G.GL_ARRAY_BUFFER, vbo)
    coords = np.array([-1, -1, 3, -1, -1, 3], dtype=np.float32)
    G.glBufferData(G.GL_ARRAY_BUFFER, coords.nbytes, coords, G.GL_STATIC_DRAW)
    names = ['gojo', 'gojo_target', 'f']
    tests = {
        'default': {}, 'clarity': {'uClarityOn': 1, 'uClarity': 100}, 'structure': {'uStructureOn': 1, 'uStructure': -100},
        'unsharp': {'uUsmOn': 1, 'uUsmAmount': 50, 'uUsmRadius': 1, 'uUsmThreshold': 0},
        'unsharp_extreme': {'uUsmOn': 1, 'uUsmAmount': 500, 'uUsmRadius': 100, 'uUsmThreshold': 15},
        'haze': dict(zip(['uHazeOn', 'uHazeIntensity', 'uHazeThreshold', 'uHazeOpacity', 'uHazeSpread', 'uHazeBand',
                          'uHazeSide', 'uHazeCenter', 'uHazeGain', 'uHazeMode'], [1,.4,.5,.4,.35,.03,.4,0,3.5,0])),
    }
    tests['haze_extreme'] = dict(zip(tests['haze'], [1,1,0,1,.6,.1,.12,1,15,1]))
    for name in names:
        source = np.asarray(Image.open(path / f'{name}_input.png').convert('RGBA'))
        h, w = source.shape[:2]
        dest_w, dest_h = max(1, int(w*.25+.5)), max(1, int(h*.25+.5))
        orig = texture(w, h, np.ascontiguousarray(source[::-1]))
        results = []
        for label, options in tests.items():
            def go(shader, src, dims=(w, h), prev=None, floating=False):
                return draw(programs[shader], src, *dims, {k: options.get(k,0) for k in set().union(*[t.keys() for t in tests.values()])},
                            previous=prev, floating=floating)
            detail = go('detail', orig)
            blur = go('usm_horizontal', detail, floating=True) if options.get('uUsmOn') else detail
            usm = go('usm_composite', blur, prev=detail)
            haze = usm
            if options.get('uHazeOn'):
                for pass_name in ['haze_extract','haze_horizontal','haze_vertical']:
                    haze = go(pass_name, haze, dims=(dest_w,dest_h), floating=True)
            go('haze_composite', haze, prev=usm)
            actual = np.frombuffer(G.glReadPixels(0,0,w,h,G.GL_RGBA,G.GL_UNSIGNED_BYTE), np.uint8).reshape(h,w,4)[::-1]
            expected = source if label == 'default' else np.asarray(Image.open(path / f'{name}_{label}.png').convert('RGBA'))
            error = abs(actual.astype(np.int16) - expected.astype(np.int16))
            mean, p99, high = error.mean(), np.percentile(error,99), error.max()
            print(f'{name:11s} {label:16s} mean={mean:.3f}  p99={p99:.1f}  max={high}')
            assert np.array_equal(actual[:,:,3], expected[:,:,3]), (name,label,'alpha')
            assert (high == 0 if label == 'default' else mean <= 1.0 and p99 <= 7 and high <= 18), (name,label,mean,p99,high)
            results.append(Image.fromarray(actual).convert('RGB'))
        panel = Image.new('RGB',(w*4,h*2))
        for index, img in enumerate(results): panel.paste(img,(index%4*w,index//4*h))
        panel.save(path / f'{name}_gles.png')


if __name__ == '__main__':
    main(Path(sys.argv[1]))
