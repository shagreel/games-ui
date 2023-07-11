import './App.css';
import {Routes, Route} from "react-router-dom";
import { List } from './components/list';
import { Admin } from './components/admin';

function App() {
    alloy("sendEvent", {
        "xdm": {
            "web": {
                "webPageDetails": {
                    "pageViews": {
                        "value": 1
                    },
                    "name": "Game List",
                    "isHomePage": true,
                    "URL": window.location.href,
                    "server": window.location.hostname
                }
            }
        }
    });
    return (
        <Routes>
            <Route path="/" element={<List />} />
            <Route path="/borrowed" element={<Admin/>} />
        </Routes>
    );
}

export default App;
