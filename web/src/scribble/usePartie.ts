import { useCallback, useEffect, useRef, useState } from "react";
import type { Lang } from "../i18n";
import type { Joueur } from "../relay/relay";
import { champions } from "./champions";
import {
  avancer,
  choisir,
  deviner,
  estPartie,
  nouvellePartie,
  type Partie,
  presence,
} from "./partie";

export type MessageJeu =
  | { jeu: "etat"; partie: Partie | null }
  | { jeu: "devine"; texte: string }
  | { jeu: "choisi"; index: number }
  | { jeu: "proche"; pour: string };

export const estMessageJeu = (data: unknown): data is MessageJeu =>
  typeof (data as { jeu?: unknown } | null)?.jeu === "string";

const TICK_MS = 500;
// L'état part aussi sans changement : un nouveau meneur reprend à quelques secondes près.
const RESYNCHRO_MS = 5_000;
const PROCHE_MS = 2_500;

interface Options {
  moi: string;
  meneur: string | null;
  joueurs: Joueur[];
  lang: Lang;
  envoie: (message: MessageJeu) => void;
  efface: () => void;
}

export function usePartie({
  moi,
  meneur,
  joueurs,
  lang,
  envoie,
  efface,
}: Options) {
  const [partie, setPartie] = useState<Partie | null>(null);
  const [recuA, setRecuA] = useState(0);
  const [proche, setProche] = useState(false);
  const [erreur, setErreur] = useState(false);
  const courante = useRef<Partie | null>(null);
  const presents = useRef(joueurs);
  presents.current = joueurs;
  const meneurCourant = useRef(meneur);
  meneurCourant.current = meneur;
  const estMeneur = meneur === moi;

  const garde = useCallback((suivante: Partie | null) => {
    courante.current = suivante;
    setPartie(suivante);
    setRecuA(Date.now());
  }, []);

  const publie = useCallback(
    (suivante: Partie | null) => {
      const precedente = courante.current;
      garde(suivante);
      envoie({ jeu: "etat", partie: suivante });
      const nouvelleManche =
        suivante?.phase === "choix" &&
        (precedente?.phase !== "choix" || precedente.tour !== suivante.tour);
      if (nouvelleManche) {
        efface();
      }
    },
    [efface, envoie, garde],
  );

  const juge = useCallback(
    (auteur: string, texte: string) => {
      const p = courante.current;
      if (!p) {
        return;
      }
      const { partie: suivante, proche: presque } = deviner(p, auteur, texte);
      if (presque) {
        if (auteur === moi) {
          setProche(true);
        } else {
          envoie({ jeu: "proche", pour: auteur });
        }
      }
      if (suivante !== p) {
        publie(suivante);
      }
    },
    [envoie, moi, publie],
  );

  const recoit = useCallback(
    (de: string | null, message: MessageJeu) => {
      const p = courante.current;
      const jeMene = meneurCourant.current === moi;
      switch (message.jeu) {
        case "etat":
          if (
            de === meneurCourant.current &&
            (message.partie === null || estPartie(message.partie))
          ) {
            garde(message.partie);
          }
          break;
        case "devine":
          if (jeMene && de && typeof message.texte === "string") {
            juge(de, message.texte);
          }
          break;
        case "choisi":
          if (jeMene && p && de === p.dessinateur) {
            publie(choisir(p, message.index));
          }
          break;
        case "proche":
          if (message.pour === moi) {
            setProche(true);
          }
          break;
      }
    },
    [garde, juge, moi, publie],
  );

  useEffect(() => {
    if (!proche) {
      return;
    }
    const minuterie = window.setTimeout(() => setProche(false), PROCHE_MS);
    return () => window.clearTimeout(minuterie);
  }, [proche]);

  // Le meneur fait avancer le temps ; s'il vient de prendre la main, il repart du dernier état reçu.
  useEffect(() => {
    if (!estMeneur || !partie || partie.phase === "fin") {
      return;
    }
    let dernier = recuA;
    let diffuse = Date.now();
    const minuterie = window.setInterval(() => {
      const p = courante.current;
      if (!p) {
        return;
      }
      const maintenant = Date.now();
      const suivante = avancer(
        p,
        maintenant - dernier,
        presents.current,
        Math.random,
      );
      dernier = maintenant;
      const change =
        suivante.phase !== p.phase ||
        suivante.tour !== p.tour ||
        suivante.revele.length !== p.revele.length;
      if (change || maintenant - diffuse >= RESYNCHRO_MS) {
        diffuse = maintenant;
        publie(suivante);
      } else {
        courante.current = suivante;
      }
    }, TICK_MS);
    return () => window.clearInterval(minuterie);
    // La boucle ne se relance qu'à un changement de meneur ou de phase, pas à chaque état publié.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [estMeneur, partie?.phase, partie?.tour]);

  // Qui arrive reçoit l'état ; qui part est retiré des manches à venir.
  useEffect(() => {
    const p = courante.current;
    if (estMeneur && p && p.phase !== "fin") {
      publie(presence(p, joueurs));
    }
  }, [estMeneur, joueurs, publie]);

  const lance = useCallback(
    (duree: number) => {
      setErreur(false);
      void champions(lang)
        .then((mots) =>
          publie(
            nouvellePartie(presents.current, duree, lang, mots, Math.random),
          ),
        )
        .catch(() => setErreur(true));
    },
    [lang, publie],
  );

  const devine = useCallback(
    (texte: string) => {
      if (estMeneur) {
        juge(moi, texte);
      } else {
        envoie({ jeu: "devine", texte });
      }
    },
    [envoie, estMeneur, juge, moi],
  );

  const choisis = useCallback(
    (index: number) => {
      const p = courante.current;
      if (estMeneur && p) {
        publie(choisir(p, index));
      } else {
        envoie({ jeu: "choisi", index });
      }
    },
    [envoie, estMeneur, publie],
  );

  return {
    partie,
    courante,
    recuA,
    proche,
    erreur,
    estMeneur,
    recoit,
    lance,
    devine,
    choisis,
  };
}
