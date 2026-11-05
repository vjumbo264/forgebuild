"""
REFERENCE COLOUR MATCH (no LUT). Python prototype used to prove the method on the Gojo pair.
Usage: python reference_match.py gojo_before.png gojo_target.png match_panel.png
Stages: (0) robust alignment of target to base (ECC affine on edge maps), (1) 3x3 matrix + offset in linear light (Huber IRLS),
(2) per-channel monotone tone curves (48 equal-count bins, isotonic, light smoothing, PCHIP),
(3) hue-keyed stage in CIELAB: 24 periodic hue bins -> dL, chroma scale, hue rotation, weighted by C/(C+10) so greys stay clean, 4 refinement rounds.
Measured on the Gojo pair (CIEDE2000): identity mean 10.12 / p95 24.54; matched mean 4.86 / p95 11.36; holdout (right 20 percent, not used for fitting) 4.83 / 9.93.
Port this faithfully to Kotlin in `core`; the exported shader must implement the same stages.
"""
import cv2, numpy as np, json, sys
from skimage.color import rgb2lab, lab2rgb, deltaE_ciede2000
from scipy.interpolate import PchipInterpolator
BASE=sys.argv[1] if len(sys.argv)>1 else 'gojo_before.png'
TARGET=sys.argv[2] if len(sys.argv)>2 else 'gojo_target.png'
PANEL=sys.argv[3] if len(sys.argv)>3 else 'match_panel.png'
b=cv2.cvtColor(cv2.imread(BASE),cv2.COLOR_BGR2RGB).astype(np.float32)/255
t0=cv2.cvtColor(cv2.imread(TARGET),cv2.COLOR_BGR2RGB).astype(np.float32)/255
H,W=b.shape[:2]

def edges(x):
    g=cv2.cvtColor(x,cv2.COLOR_RGB2GRAY); g=cv2.GaussianBlur(g,(0,0),1.2)
    gx=cv2.Sobel(g,cv2.CV_32F,1,0); gy=cv2.Sobel(g,cv2.CV_32F,0,1)
    return np.sqrt(gx*gx+gy*gy)
warp=np.eye(2,3,dtype=np.float32)
crit=(cv2.TERM_CRITERIA_EPS|cv2.TERM_CRITERIA_COUNT,300,1e-6)
cc,warp=cv2.findTransformECC(edges(b),edges(t0),warp,cv2.MOTION_AFFINE,crit,None,5)
print('ECC corr',round(float(cc),4),'warp',np.round(warp,4).tolist())
t=cv2.warpAffine(t0,warp,(W,H),flags=cv2.INTER_LINEAR+cv2.WARP_INVERSE_MAP,borderMode=cv2.BORDER_REPLICATE)
valid=np.zeros((H,W),bool); valid[6:-6,6:-6]=True
train=valid.copy(); train[:,int(W*0.8):]=False
hold=valid.copy(); hold[:,:int(W*0.8)]=False

def s2l(x): return np.where(x<=0.04045,x/12.92,((x+0.055)/1.055)**2.4)
def l2s(x):
    x=np.clip(x,0,1); return np.where(x<=0.0031308,x*12.92,1.055*np.power(x,1/2.4)-0.055)
def lab(x): return rgb2lab(np.clip(x,0,1))
def de(a,bb): return deltaE_ciede2000(lab(a),lab(bb))
def wrap(a): return (a+np.pi)%(2*np.pi)-np.pi
NB=24

class Model:
    def __init__(s):
        s.A=np.eye(3); s.o=np.zeros(3); s.curves=None; s.hue=np.zeros((NB,3))
    def stage12(s,p):
        g=l2s(s2l(p)@s.A.T+s.o)
        if s.curves is not None:
            g=np.stack([np.clip(PchipInterpolator(*s.curves[c],extrapolate=True)(g[:,c]),0,1) for c in range(3)],1)
        return np.clip(g,0,1)
    def hue_apply(s,g):
        L=rgb2lab(g.reshape(1,-1,3))[0]
        h=np.arctan2(L[:,2],L[:,1]); C=np.hypot(L[:,1],L[:,2]); w=C/(C+10.0)
        k=(h/(2*np.pi))%1.0*NB; i0=np.floor(k).astype(int)%NB; f=k-np.floor(k); i1=(i0+1)%NB
        P=s.hue[i0]*(1-f)[:,None]+s.hue[i1]*f[:,None]
        C2=C*(1+P[:,1]*w); h2=h+P[:,2]*w
        out=np.stack([L[:,0]+P[:,0]*w,C2*np.cos(h2),C2*np.sin(h2)],1)
        return np.clip(lab2rgb(out.reshape(1,-1,3))[0],0,1)
    def apply(s,x):
        sh=x.shape; g=s.stage12(x.reshape(-1,3)); return s.hue_apply(g).reshape(sh).astype(np.float32)

def fit_matrix(pb,pt,mask):
    X=np.c_[s2l(pb),np.ones(len(pb))]; Y=s2l(pt); w=np.ones(len(pb))
    for _ in range(4):
        Xw=X*w[:,None]; B=np.linalg.solve(Xw.T@X+1e-4*np.eye(4),Xw.T@Y)   # 4x3
        r=np.abs(X@B-Y).max(1); w=1/np.maximum(1,r/0.06)
    return B[:3].T,B[3]

def fit_curves(g,tg,bins=48):
    cur=[]
    for c in range(3):
        o=np.argsort(g[:,c]); x=g[o,c]; y=tg[o,c]
        e=np.linspace(0,len(x),bins+1).astype(int)
        xs=np.array([x[a:q].mean() for a,q in zip(e[:-1],e[1:])]); ys=np.array([y[a:q].mean() for a,q in zip(e[:-1],e[1:])])
        ys=np.maximum.accumulate(ys); ys=np.convolve(np.pad(ys,2,mode='edge'),np.ones(5)/5,'valid')
        xs=np.r_[0.0,xs,1.0]; ys=np.r_[np.clip(ys[0]-xs[1],0,1),ys,np.clip(ys[-1]+1-xs[-2],0,1)]
        ys=np.maximum.accumulate(ys); keep=np.r_[True,np.diff(xs)>1e-4]; cur.append((xs[keep],ys[keep]))
    return cur

def fit_hue(model,pb,pt):
    g=model.stage12(pb)
    cur=model.hue_apply(g)
    Lc=rgb2lab(cur.reshape(1,-1,3))[0]; Lt=rgb2lab(pt.reshape(1,-1,3))[0]
    hc=np.arctan2(Lc[:,2],Lc[:,1]); Cc=np.hypot(Lc[:,1],Lc[:,2]); ht=np.arctan2(Lt[:,2],Lt[:,1]); Ct=np.hypot(Lt[:,1],Lt[:,2])
    w=Cc/(Cc+10.0)
    # work in the pre-hue-stage hue for binning (that is what apply() uses)
    Lg=rgb2lab(g.reshape(1,-1,3))[0]; hg=np.arctan2(Lg[:,2],Lg[:,1]); Cg=np.hypot(Lg[:,1],Lg[:,2]); wg=Cg/(Cg+10.0)
    k=(hg/(2*np.pi))%1.0*NB; i0=np.floor(k).astype(int)%NB; f=k-np.floor(k); i1=(i0+1)%NB
    res=np.c_[Lt[:,0]-Lc[:,0], np.where(Cc>3,Ct/np.maximum(Cc,1e-3)-1,0), np.where(Cc>3,wrap(ht-hc),0)]
    res[:,1]=np.clip(res[:,1],-0.8,0.8); res[:,2]=np.clip(res[:,2],-1.0,1.0)
    num=np.zeros((NB,3)); den=np.zeros(NB)
    for idx,kw in ((i0,1-f),(i1,f)):
        for j in range(3): num[:,j]+=np.bincount(idx,weights=kw*wg*res[:,j],minlength=NB)
        den+=np.bincount(idx,weights=kw*wg*wg,minlength=NB)
    delta=num/(den[:,None]+3.0)
    # circular smoothing across hue bins
    delta=0.25*np.roll(delta,1,0)+0.5*delta+0.25*np.roll(delta,-1,0)
    model.hue=model.hue+delta

def fit(pb,pt,hue_rounds=4):
    m=Model()
    m.A,m.o=fit_matrix(pb,pt,None)
    g=l2s(s2l(pb)@m.A.T+m.o)
    m.curves=fit_curves(np.clip(g,0,1),pt)
    for _ in range(hue_rounds): fit_hue(m,pb,pt)
    return m

if __name__=='__main__':
    ptr_b=b[train]; ptr_t=t[train]
    m=fit(ptr_b,ptr_t)
    out=m.apply(b)
    base=de(b,t); fitd=de(out,t)
    def st(d,mask): return round(float(d[mask].mean()),2),round(float(np.percentile(d[mask],95)),2)
    print('identity  train',st(base,train),'holdout',st(base,hold),'all',st(base,valid))
    print('matched   train',st(fitd,train),'holdout',st(fitd,hold),'all',st(fitd,valid))
    # panel
    heat=cv2.applyColorMap(np.clip(fitd/15*255,0,255).astype(np.uint8),cv2.COLORMAP_INFERNO)
    heat=cv2.cvtColor(heat,cv2.COLOR_BGR2RGB).astype(np.float32)/255
    top=np.concatenate([b,t],1); bot=np.concatenate([out,heat],1)
    panel=(np.concatenate([top,bot],0)*255).astype(np.uint8)
    panel=cv2.resize(panel,None,fx=1.4,fy=1.4,interpolation=cv2.INTER_CUBIC)
    cv2.imwrite(PANEL,cv2.cvtColor(panel,cv2.COLOR_RGB2BGR))
    json.dump({'A':m.A.tolist(),'o':m.o.tolist(),'hue':m.hue.tolist(),'curves':[[x.tolist(),y.tolist()] for x,y in m.curves]},open('match_model.json','w'))
