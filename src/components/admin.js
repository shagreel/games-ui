import '../App.css';
import React, {useContext, useEffect, useState} from 'react';
import Cookies from 'js-cookie';
import {Tracker, WebSdkContext} from "../WebSdkContext";

const gameData = await fetch("https://public.chill.ws/games.json")
    .then((response) => response.json());

export const Borrowed = (borrowed) => {
    const game = gameData.find(g => g.id === borrowed.id);
    return (
        <tr>
            <td>{game.name}</td>
            <td>{borrowed.borrowed.name}</td>
            <td><a href={'mailto:'+ borrowed.borrowed.email}>{borrowed.borrowed.email}</a></td>
            <td>{borrowed.borrowed.date}</td>
        </tr>
    );
};

export const Admin = () => {
    const [games, setGames] = useState([]);

    useEffect(() => {
        const getGames = async () => {
            const resp = await fetch(`${process.env.REACT_APP_API_ENDPOINT}/games/borrowed`,
                {
                    credentials: 'include',
                    headers: {'x-cfp': Cookies.get('CFP-Auth-Key')}
                });
            const gamesResp = await resp.json();
            const borrowed = gamesResp.filter((g) => g.borrowed);
            borrowed.sort((a, b) => a.borrowed.date.localeCompare(b.borrowed.date));
            setGames(borrowed);
        };
        if (games.length === 0) {
            getGames();
        }
    }, [games]);

    const alloy = useContext(WebSdkContext);
    useEffect(() => {
        Tracker.trackPageView(alloy,"Borrowed List");
    }, [alloy]);

    return (
        <div>
            <table className="admin"><tbody>
            <tr>
                <th>Game</th>
                <th>Name</th>
                <th>Email</th>
                <th>Date</th>
            </tr>
            {games.map(game => Borrowed(game))}
            </tbody></table>
        </div>
    );
};